package com.skillshare.skillsharebackend.web;

import com.skillshare.skillsharebackend.payment.PaymentService;
import com.skillshare.skillsharebackend.web.dto.CreateOrderResponse;
import com.skillshare.skillsharebackend.web.dto.PaymentResponse;
import com.skillshare.skillsharebackend.web.dto.VerifyPaymentRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Phase 6 - Razorpay payment HTTP surface, wrapping {@link PaymentService}.
 * Same thin-controller split as {@link BookingController}.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** Creates (or idempotently re-returns) the Razorpay order for a
     *  confirmed booking - the frontend uses the response to open
     *  Checkout.js. */
    @PostMapping("/bookings/{bookingId}/order")
    public CreateOrderResponse createOrder(@PathVariable Long bookingId) {
        return paymentService.createOrder(bookingId);
    }

    /** The frontend's post-checkout callback - verifies the signature,
     *  then confirms via Razorpay's own API before marking the payment
     *  completed. */
    @PostMapping("/bookings/{bookingId}/verify")
    public PaymentResponse verify(@PathVariable Long bookingId, @RequestBody VerifyPaymentRequest request) {
        return paymentService.verifyCheckout(bookingId, request);
    }

    @GetMapping("/bookings/{bookingId}")
    public PaymentResponse getPayment(@PathVariable Long bookingId) {
        return paymentService.getPayment(bookingId);
    }

    /**
     * Razorpay's server-to-server webhook. Reads the raw request body
     * directly off {@link HttpServletRequest} rather than binding
     * {@code @RequestBody String} - Spring's JSON message converter would
     * otherwise try to deserialize the body as a JSON-encoded string
     * literal (since the request's Content-Type is application/json),
     * which fails for an actual JSON object and would in any case risk
     * not preserving the exact bytes Razorpay signed. Signature
     * verification needs the untouched raw body.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            HttpServletRequest request,
            @RequestHeader("X-Razorpay-Signature") String signature) throws IOException {
        String rawBody;
        try (var reader = request.getReader()) {
            rawBody = reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        paymentService.handleWebhook(rawBody, signature);
        return ResponseEntity.ok("ok");
    }
}
