package com.skillshare.skillsharebackend.payment;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.skillshare.skillsharebackend.allocation.BookingNotFoundException;
import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.Payment;
import com.skillshare.skillsharebackend.domain.enums.BookingStatus;
import com.skillshare.skillsharebackend.domain.enums.PaymentMethod;
import com.skillshare.skillsharebackend.domain.enums.PaymentStatus;
import com.skillshare.skillsharebackend.repository.BookingRepository;
import com.skillshare.skillsharebackend.repository.PaymentRepository;
import com.skillshare.skillsharebackend.web.dto.CreateOrderResponse;
import com.skillshare.skillsharebackend.web.dto.PaymentResponse;
import com.skillshare.skillsharebackend.web.dto.VerifyPaymentRequest;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

/**
 * Phase 6 - Payment & Transaction Management, resolved in favor of a real
 * gateway (Razorpay, Test/sandbox mode - see dev-status-and-next-steps.md
 * for the decision). Three server-side operations, matching Razorpay's
 * own recommended Standard Checkout integration:
 *
 * <ol>
 *   <li>{@link #createOrder} - creates a Razorpay order for a confirmed
 *       booking's {@code total_amount} (computed by {@code
 *       sp_allocate_worker} at allocation time, V6 migration). Returns
 *       everything a Checkout.js frontend needs to open the payment
 *       sheet.</li>
 *   <li>{@link #verifyCheckout} - the frontend's post-checkout callback
 *       posts back {@code razorpay_order_id}/{@code razorpay_payment_id}/
 *       {@code razorpay_signature}; this verifies the HMAC signature
 *       first (rejects a forged/tampered callback before touching
 *       anything else), then - critically - re-fetches the payment from
 *       Razorpay's own API rather than trusting any client-supplied
 *       status, and only marks it {@code completed} if Razorpay itself
 *       reports it {@code captured}.</li>
 *   <li>{@link #handleWebhook} - the server-to-server event Razorpay
 *       sends independent of whether the browser ever calls back (covers
 *       the case where the customer closes the tab right after paying).
 *       Verifies the webhook's own HMAC signature over the raw request
 *       body, then applies the same completion logic as
 *       {@link #verifyCheckout} - both paths are idempotent, since either
 *       one might arrive first, or both might arrive for the same
 *       payment.</li>
 * </ol>
 *
 * <p>No new notification code was needed here either: {@code
 * trg_notify_payment_received} (04_triggers_v3.sql) already fires on any
 * {@code payments} row reaching {@code status = 'completed'}, regardless
 * of which of the two paths above got there first - same "let the
 * trigger own it" principle every earlier phase's status-change
 * notifications follow.
 */
@Service
@Slf4j
public class PaymentService {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final RazorpayClient razorpayClient;
    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;

    public PaymentService(
            BookingRepository bookingRepository,
            PaymentRepository paymentRepository,
            RazorpayClient razorpayClient,
            @Value("${razorpay.key-id:}") String keyId,
            @Value("${razorpay.key-secret:}") String keySecret,
            @Value("${razorpay.webhook-secret:}") String webhookSecret) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.razorpayClient = razorpayClient;
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
    }

    @Transactional
    public CreateOrderResponse createOrder(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (booking.getStatus() == BookingStatus.pending || booking.getStatus() == BookingStatus.cancelled) {
            throw new PaymentNotAllowedException(
                    "Booking " + bookingId + " is not yet payable - current status is " + booking.getStatus()
                            + " (a worker must be confirmed first)");
        }
        if (booking.getTotalAmount() == null) {
            // Shouldn't happen post-V6 (sp_allocate_worker computes it),
            // but a booking confirmed before that migration ran wouldn't
            // have it - guard rather than send Razorpay a null amount.
            throw new PaymentValidationException(
                    "Booking " + bookingId + " has no total_amount computed yet");
        }

        Payment payment = paymentRepository.findByBooking_BookingId(bookingId).orElse(null);
        if (payment != null) {
            if (payment.getStatus() == PaymentStatus.completed) {
                throw new PaymentNotAllowedException("Booking " + bookingId + " has already been paid");
            }
            if (payment.getGatewayOrderId() != null) {
                // Idempotent: a pending order already exists for this
                // booking - hand back the same one rather than creating a
                // second Razorpay order for it.
                return toOrderResponse(payment);
            }
        } else {
            payment = Payment.builder()
                    .booking(booking)
                    .amount(booking.getTotalAmount())
                    .status(PaymentStatus.pending)
                    .build();
        }

        int amountInPaise = toPaise(booking.getTotalAmount());
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amountInPaise);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "booking_" + bookingId);

        Order order;
        try {
            order = razorpayClient.orders.create(orderRequest);
        } catch (RazorpayException ex) {
            throw new PaymentGatewayException(
                    "Failed to create Razorpay order for booking " + bookingId + ": " + ex.getMessage(), ex);
        }

        payment.setGatewayOrderId(order.get("id"));
        payment = paymentRepository.save(payment);

        log.info("PaymentService: order {} created for booking {} ({} paise)",
                payment.getGatewayOrderId(), bookingId, amountInPaise);

        return toOrderResponse(payment);
    }

    @Transactional
    public PaymentResponse verifyCheckout(Long bookingId, VerifyPaymentRequest request) {
        Payment payment = paymentRepository.findByBooking_BookingId(bookingId)
                .orElseThrow(() -> new PaymentNotAllowedException(
                        "No order exists yet for booking " + bookingId));

        if (payment.getStatus() == PaymentStatus.completed) {
            // Idempotent: the webhook may have already confirmed this
            // payment before the checkout callback reached us.
            return PaymentResponse.from(payment);
        }
        if (payment.getGatewayOrderId() == null
                || !payment.getGatewayOrderId().equals(request.razorpayOrderId())) {
            throw new PaymentVerificationException(
                    "razorpay_order_id does not match the order on file for booking " + bookingId);
        }

        JSONObject attributes = new JSONObject();
        attributes.put("razorpay_order_id", request.razorpayOrderId());
        attributes.put("razorpay_payment_id", request.razorpayPaymentId());
        attributes.put("razorpay_signature", request.razorpaySignature());

        boolean signatureValid;
        try {
            signatureValid = Utils.verifyPaymentSignature(attributes, keySecret);
        } catch (RazorpayException ex) {
            throw new PaymentVerificationException("Signature verification failed: " + ex.getMessage());
        }
        if (!signatureValid) {
            throw new PaymentVerificationException("razorpay_signature is invalid for booking " + bookingId);
        }

        // Never trust the client's word that a payment succeeded - the
        // signature only proves the CALLBACK wasn't forged, not that
        // Razorpay actually captured money. Re-fetch the payment from
        // Razorpay's own API and only proceed if it agrees.
        com.razorpay.Payment razorpayPayment;
        try {
            razorpayPayment = razorpayClient.payments.fetch(request.razorpayPaymentId());
        } catch (RazorpayException ex) {
            throw new PaymentGatewayException(
                    "Failed to fetch payment " + request.razorpayPaymentId() + " from Razorpay: " + ex.getMessage(),
                    ex);
        }
        String status = razorpayPayment.get("status");
        if (!"captured".equals(status)) {
            throw new PaymentVerificationException(
                    "Razorpay reports payment " + request.razorpayPaymentId() + " as \"" + status
                            + "\", not \"captured\"");
        }

        applyCompletion(payment, request.razorpayPaymentId(), request.razorpaySignature(),
                razorpayPayment.get("method"));

        log.info("PaymentService: booking {} payment verified and completed ({})", bookingId,
                request.razorpayPaymentId());

        return PaymentResponse.from(payment);
    }

    @Transactional
    public void handleWebhook(String rawBody, String signatureHeader) {
        boolean signatureValid;
        try {
            signatureValid = Utils.verifyWebhookSignature(rawBody, signatureHeader, webhookSecret);
        } catch (RazorpayException ex) {
            throw new PaymentVerificationException("Webhook signature verification failed: " + ex.getMessage());
        }
        if (!signatureValid) {
            throw new PaymentVerificationException("Webhook X-Razorpay-Signature is invalid");
        }

        JSONObject payload = new JSONObject(rawBody);
        String event = payload.optString("event", "");
        if (!"payment.captured".equals(event)) {
            // Any other subscribed/unsubscribed event type - nothing for
            // us to do. Acknowledge rather than error, so Razorpay
            // doesn't retry it.
            log.info("PaymentService: ignoring webhook event \"{}\"", event);
            return;
        }

        JSONObject paymentEntity = payload
                .getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");
        String orderId = paymentEntity.getString("order_id");
        String paymentId = paymentEntity.getString("id");
        String method = paymentEntity.optString("method", null);

        Payment payment = paymentRepository.findByGatewayOrderId(orderId).orElse(null);
        if (payment == null) {
            // Order id we don't recognize - could be a test event from the
            // Razorpay dashboard, or a payment created outside this app.
            // Acknowledge (200) rather than error either way.
            log.warn("PaymentService: webhook for unknown order {} (payment {})", orderId, paymentId);
            return;
        }
        if (payment.getStatus() == PaymentStatus.completed) {
            // Idempotent: verifyCheckout already completed this one.
            return;
        }

        applyCompletion(payment, paymentId, null, method);

        log.info("PaymentService: booking {} payment completed via webhook ({})",
                payment.getBooking().getBookingId(), paymentId);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long bookingId) {
        Payment payment = paymentRepository.findByBooking_BookingId(bookingId)
                .orElseThrow(() -> new PaymentNotAllowedException("No payment exists yet for booking " + bookingId));
        return PaymentResponse.from(payment);
    }

    private void applyCompletion(Payment payment, String gatewayPaymentId, String gatewaySignature, String method) {
        payment.setStatus(PaymentStatus.completed);
        payment.setGatewayPaymentId(gatewayPaymentId);
        if (gatewaySignature != null) {
            payment.setGatewaySignature(gatewaySignature);
        }
        payment.setMethod(mapMethod(method));
        payment.setPaidAt(OffsetDateTime.now());
        paymentRepository.save(payment);
    }

    /** Razorpay's {@code method} field has more values ({@code card},
     *  {@code upi}, {@code netbanking}, {@code wallet}, {@code emi}, ...)
     *  than our {@code payment_method} enum ({@code cash}, {@code card},
     *  {@code upi}, {@code wallet}). Anything we don't have a slot for
     *  (netbanking, emi) is left {@code null} rather than force-fit into
     *  the wrong category - {@code payments.method} is nullable exactly
     *  for cases like this. */
    private static PaymentMethod mapMethod(String razorpayMethod) {
        if (razorpayMethod == null) {
            return null;
        }
        return switch (razorpayMethod) {
            case "card" -> PaymentMethod.card;
            case "upi" -> PaymentMethod.upi;
            case "wallet" -> PaymentMethod.wallet;
            default -> null;
        };
    }

    private static int toPaise(BigDecimal rupees) {
        return rupees.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private CreateOrderResponse toOrderResponse(Payment payment) {
        return new CreateOrderResponse(
                payment.getPaymentId(),
                payment.getBooking().getBookingId(),
                keyId,
                payment.getGatewayOrderId(),
                toPaise(payment.getAmount()),
                payment.getAmount(),
                "INR",
                payment.getStatus());
    }
}
