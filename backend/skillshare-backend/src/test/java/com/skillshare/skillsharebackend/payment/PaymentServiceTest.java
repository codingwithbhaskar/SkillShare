package com.skillshare.skillsharebackend.payment;

import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import com.skillshare.skillsharebackend.allocation.BookingNotFoundException;
import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.Payment;
import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.domain.enums.BookingStatus;
import com.skillshare.skillsharebackend.domain.enums.PaymentStatus;
import com.skillshare.skillsharebackend.repository.BookingRepository;
import com.skillshare.skillsharebackend.repository.PaymentRepository;
import com.skillshare.skillsharebackend.web.dto.CreateOrderResponse;
import com.skillshare.skillsharebackend.web.dto.PaymentResponse;
import com.skillshare.skillsharebackend.web.dto.VerifyPaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentService}'s own logic - guards, idempotency
 * short-circuits, and the two signature-verification failure paths.
 * {@link RazorpayClient} is mocked and, wherever a test would need it to
 * actually return an {@code Order}/{@code Payment}, that path is left
 * untested here on purpose: both of those SDK classes only expose a
 * package-private constructor (verified against the SDK source), so a
 * real success response can't be built from this test package at all -
 * same boundary {@code AllocationServiceTest} draws around the real
 * {@code CALL sp_allocate_worker(?)} round-trip. Real order creation and
 * real signature verification are proven live once Test Mode keys exist
 * (dev-status-and-next-steps.md).
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private BookingRepository bookingRepository;
    private PaymentRepository paymentRepository;
    private RazorpayClient razorpayClient;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        bookingRepository = Mockito.mock(BookingRepository.class);
        paymentRepository = Mockito.mock(PaymentRepository.class);
        razorpayClient = Mockito.mock(RazorpayClient.class);
        service = new PaymentService(bookingRepository, paymentRepository, razorpayClient,
                "rzp_test_key", "test_secret", "test_webhook_secret");
    }

    private Booking bookingWith(BookingStatus status, BigDecimal totalAmount) {
        return Booking.builder()
                .bookingId(4L)
                .status(status)
                .totalAmount(totalAmount)
                .customer(User.builder().userId(1L).build())
                .build();
    }

    // ---- createOrder ----------------------------------------------------

    @Test
    void createOrder_throwsBookingNotFoundException_whenBookingMissing() {
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BookingNotFoundException.class, () -> service.createOrder(999L));
        verifyNoInteractions(razorpayClient);
    }

    @Test
    void createOrder_throwsPaymentNotAllowedException_whenBookingStillPending() {
        when(bookingRepository.findById(4L))
                .thenReturn(Optional.of(bookingWith(BookingStatus.pending, new BigDecimal("500.00"))));

        assertThrows(PaymentNotAllowedException.class, () -> service.createOrder(4L));
        verifyNoInteractions(razorpayClient);
    }

    @Test
    void createOrder_throwsPaymentNotAllowedException_whenBookingCancelled() {
        when(bookingRepository.findById(4L))
                .thenReturn(Optional.of(bookingWith(BookingStatus.cancelled, new BigDecimal("500.00"))));

        assertThrows(PaymentNotAllowedException.class, () -> service.createOrder(4L));
    }

    @Test
    void createOrder_throwsPaymentValidationException_whenTotalAmountNull() {
        when(bookingRepository.findById(4L))
                .thenReturn(Optional.of(bookingWith(BookingStatus.confirmed, null)));

        assertThrows(PaymentValidationException.class, () -> service.createOrder(4L));
        verifyNoInteractions(razorpayClient);
    }

    @Test
    void createOrder_throwsPaymentNotAllowedException_whenAlreadyPaid() {
        Booking booking = bookingWith(BookingStatus.completed, new BigDecimal("500.00"));
        when(bookingRepository.findById(4L)).thenReturn(Optional.of(booking));
        Payment existing = Payment.builder().paymentId(1L).booking(booking).status(PaymentStatus.completed).build();
        when(paymentRepository.findByBooking_BookingId(4L)).thenReturn(Optional.of(existing));

        assertThrows(PaymentNotAllowedException.class, () -> service.createOrder(4L));
        verifyNoInteractions(razorpayClient);
    }

    @Test
    void createOrder_returnsExistingOrder_whenPendingOrderAlreadyExists() {
        Booking booking = bookingWith(BookingStatus.confirmed, new BigDecimal("500.00"));
        when(bookingRepository.findById(4L)).thenReturn(Optional.of(booking));
        Payment existing = Payment.builder()
                .paymentId(1L).booking(booking).amount(new BigDecimal("500.00"))
                .status(PaymentStatus.pending).gatewayOrderId("order_existing123").build();
        when(paymentRepository.findByBooking_BookingId(4L)).thenReturn(Optional.of(existing));

        CreateOrderResponse response = service.createOrder(4L);

        assertEquals("order_existing123", response.razorpayOrderId());
        assertEquals(50000, response.amountInPaise());
        verifyNoInteractions(razorpayClient);
    }

    // ---- verifyCheckout ---------------------------------------------------

    @Test
    void verifyCheckout_throwsPaymentNotAllowedException_whenNoOrderExists() {
        when(paymentRepository.findByBooking_BookingId(4L)).thenReturn(Optional.empty());

        assertThrows(PaymentNotAllowedException.class,
                () -> service.verifyCheckout(4L, new VerifyPaymentRequest("order_x", "pay_x", "sig_x")));
    }

    @Test
    void verifyCheckout_returnsExistingResponse_whenAlreadyCompleted() {
        Booking booking = bookingWith(BookingStatus.completed, new BigDecimal("500.00"));
        Payment payment = Payment.builder()
                .paymentId(1L).booking(booking).amount(new BigDecimal("500.00"))
                .status(PaymentStatus.completed).gatewayOrderId("order_x").gatewayPaymentId("pay_x").build();
        when(paymentRepository.findByBooking_BookingId(4L)).thenReturn(Optional.of(payment));

        PaymentResponse response = service.verifyCheckout(4L, new VerifyPaymentRequest("order_x", "pay_x", "sig_x"));

        assertEquals(PaymentStatus.completed, response.status());
        verifyNoInteractions(razorpayClient);
    }

    @Test
    void verifyCheckout_throwsPaymentVerificationException_whenOrderIdMismatch() {
        Booking booking = bookingWith(BookingStatus.confirmed, new BigDecimal("500.00"));
        Payment payment = Payment.builder()
                .paymentId(1L).booking(booking).amount(new BigDecimal("500.00"))
                .status(PaymentStatus.pending).gatewayOrderId("order_real").build();
        when(paymentRepository.findByBooking_BookingId(4L)).thenReturn(Optional.of(payment));

        assertThrows(PaymentVerificationException.class,
                () -> service.verifyCheckout(4L, new VerifyPaymentRequest("order_wrong", "pay_x", "sig_x")));
        verifyNoInteractions(razorpayClient);
    }

    @Test
    void verifyCheckout_throwsPaymentVerificationException_whenSignatureInvalid() {
        Booking booking = bookingWith(BookingStatus.confirmed, new BigDecimal("500.00"));
        Payment payment = Payment.builder()
                .paymentId(1L).booking(booking).amount(new BigDecimal("500.00"))
                .status(PaymentStatus.pending).gatewayOrderId("order_real").build();
        when(paymentRepository.findByBooking_BookingId(4L)).thenReturn(Optional.of(payment));

        try (MockedStatic<Utils> utils = Mockito.mockStatic(Utils.class)) {
            utils.when(() -> Utils.verifyPaymentSignature(any(), anyString())).thenReturn(false);

            assertThrows(PaymentVerificationException.class, () -> service.verifyCheckout(4L,
                    new VerifyPaymentRequest("order_real", "pay_x", "sig_forged")));
        }
        verifyNoInteractions(razorpayClient);
    }

    // ---- handleWebhook ------------------------------------------------------

    @Test
    void handleWebhook_throwsPaymentVerificationException_whenSignatureInvalid() {
        try (MockedStatic<Utils> utils = Mockito.mockStatic(Utils.class)) {
            utils.when(() -> Utils.verifyWebhookSignature(anyString(), anyString(), anyString())).thenReturn(false);

            assertThrows(PaymentVerificationException.class,
                    () -> service.handleWebhook("{\"event\":\"payment.captured\"}", "bad_sig"));
        }
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void handleWebhook_ignoresNonPaymentCapturedEvents() {
        try (MockedStatic<Utils> utils = Mockito.mockStatic(Utils.class)) {
            utils.when(() -> Utils.verifyWebhookSignature(anyString(), anyString(), anyString())).thenReturn(true);

            service.handleWebhook("{\"event\":\"order.paid\"}", "sig");
        }
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void handleWebhook_doesNotThrow_whenOrderIdUnknown() {
        String body = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
                + "{\"id\":\"pay_x\",\"order_id\":\"order_unknown\",\"method\":\"upi\"}}}}";
        when(paymentRepository.findByGatewayOrderId("order_unknown")).thenReturn(Optional.empty());

        try (MockedStatic<Utils> utils = Mockito.mockStatic(Utils.class)) {
            utils.when(() -> Utils.verifyWebhookSignature(anyString(), anyString(), anyString())).thenReturn(true);

            service.handleWebhook(body, "sig");
        }
        verify(paymentRepository, Mockito.never()).save(any());
    }

    @Test
    void handleWebhook_completesPayment_whenSignatureValidAndOrderKnown() {
        Booking booking = bookingWith(BookingStatus.confirmed, new BigDecimal("500.00"));
        Payment payment = Payment.builder()
                .paymentId(1L).booking(booking).amount(new BigDecimal("500.00"))
                .status(PaymentStatus.pending).gatewayOrderId("order_known").build();
        when(paymentRepository.findByGatewayOrderId("order_known")).thenReturn(Optional.of(payment));

        String body = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
                + "{\"id\":\"pay_y\",\"order_id\":\"order_known\",\"method\":\"upi\"}}}}";

        try (MockedStatic<Utils> utils = Mockito.mockStatic(Utils.class)) {
            utils.when(() -> Utils.verifyWebhookSignature(anyString(), anyString(), anyString())).thenReturn(true);

            service.handleWebhook(body, "sig");
        }

        assertEquals(PaymentStatus.completed, payment.getStatus());
        assertEquals("pay_y", payment.getGatewayPaymentId());
        verify(paymentRepository).save(payment);
    }

    // ---- getPayment ---------------------------------------------------------

    @Test
    void getPayment_throwsPaymentNotAllowedException_whenNoPaymentExists() {
        when(paymentRepository.findByBooking_BookingId(4L)).thenReturn(Optional.empty());

        assertThrows(PaymentNotAllowedException.class, () -> service.getPayment(4L));
    }

    @Test
    void getPayment_returnsResponse_whenExists() {
        Booking booking = bookingWith(BookingStatus.completed, new BigDecimal("500.00"));
        Payment payment = Payment.builder()
                .paymentId(1L).booking(booking).amount(new BigDecimal("500.00"))
                .status(PaymentStatus.completed).build();
        when(paymentRepository.findByBooking_BookingId(4L)).thenReturn(Optional.of(payment));

        PaymentResponse response = service.getPayment(4L);

        assertEquals(1L, response.paymentId());
        assertEquals(PaymentStatus.completed, response.status());
    }
}
