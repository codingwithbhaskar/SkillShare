package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.Payment;
import com.skillshare.skillsharebackend.domain.enums.PaymentMethod;
import com.skillshare.skillsharebackend.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Response shape for the payment status/verification endpoints.
 *  Deliberately omits {@code createdAt}/{@code updatedAt} - both are
 *  DB-generated columns Hibernate doesn't auto-refresh after a write (see
 *  {@code ReviewService}'s Phase 5 fix for the same pitfall); simplest to
 *  just not expose them here rather than add another
 *  {@code entityManager.refresh()} call for fields nothing downstream
 *  needs. {@code paidAt} is safe to expose as-is - it's set by this
 *  app's own code, not the database. */
public record PaymentResponse(
        Long paymentId,
        Long bookingId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        String gatewayOrderId,
        String gatewayPaymentId,
        OffsetDateTime paidAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getBooking().getBookingId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getGatewayOrderId(),
                payment.getGatewayPaymentId(),
                payment.getPaidAt());
    }
}
