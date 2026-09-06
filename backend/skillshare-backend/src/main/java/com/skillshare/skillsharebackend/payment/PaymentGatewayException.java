package com.skillshare.skillsharebackend.payment;

/** Thrown by {@link PaymentService} when a call to Razorpay itself fails
 *  (network error, Razorpay-side error, or - most likely right now -
 *  {@code razorpay.key-id}/{@code key-secret} not yet configured with a
 *  real Test Mode account). Mapped to HTTP 502 by
 *  {@code GlobalExceptionHandler}, since the request was well-formed but
 *  the upstream gateway is what failed, not this app. */
public class PaymentGatewayException extends RuntimeException {
    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
