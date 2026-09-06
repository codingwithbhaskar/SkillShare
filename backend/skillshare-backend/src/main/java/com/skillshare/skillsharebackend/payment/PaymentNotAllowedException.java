package com.skillshare.skillsharebackend.payment;

/** Thrown by {@link PaymentService} when an order/payment action is
 *  requested for a booking that isn't in a payable state - not yet
 *  confirmed (no worker/price assigned), already fully paid, or no
 *  order/payment exists yet to verify. Mapped to HTTP 409 by
 *  {@code GlobalExceptionHandler}. */
public class PaymentNotAllowedException extends RuntimeException {
    public PaymentNotAllowedException(String message) {
        super(message);
    }
}
