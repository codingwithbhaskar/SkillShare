package com.skillshare.skillsharebackend.web.dto;

/** Request body for {@code POST /api/payments/bookings/{bookingId}/verify}
 *  - the three fields Razorpay Checkout.js hands back to the frontend's
 *  success handler after a payment completes. */
public record VerifyPaymentRequest(
        String razorpayOrderId,
        String razorpayPaymentId,
        String razorpaySignature) {
}
