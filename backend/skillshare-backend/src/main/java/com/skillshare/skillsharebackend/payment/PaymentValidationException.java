package com.skillshare.skillsharebackend.payment;

/** Thrown by {@link PaymentService} for request-level validation failures
 *  - e.g. a booking with no {@code total_amount} yet (shouldn't happen
 *  post-V6, since {@code sp_allocate_worker} computes it, but guarded
 *  defensively). Mapped to HTTP 400 by {@code GlobalExceptionHandler}. */
public class PaymentValidationException extends RuntimeException {
    public PaymentValidationException(String message) {
        super(message);
    }
}
