package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.enums.PaymentStatus;

import java.math.BigDecimal;

/** Response for {@code POST /api/payments/bookings/{bookingId}/order} -
 *  everything a Razorpay Checkout.js frontend needs to open the payment
 *  sheet ({@code razorpayKeyId}, {@code razorpayOrderId},
 *  {@code amountInPaise}, {@code currency}), plus our own ids for
 *  tracking. {@code razorpayKeyId} is Razorpay's PUBLIC key - safe to
 *  send to the browser, unlike the key secret, which never leaves this
 *  service. */
public record CreateOrderResponse(
        Long paymentId,
        Long bookingId,
        String razorpayKeyId,
        String razorpayOrderId,
        int amountInPaise,
        BigDecimal amount,
        String currency,
        PaymentStatus status) {
}
