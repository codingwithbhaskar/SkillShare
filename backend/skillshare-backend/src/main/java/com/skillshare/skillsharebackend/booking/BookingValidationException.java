package com.skillshare.skillsharebackend.booking;

/** Thrown by {@link BookingService#createBooking} and
 *  {@link ReviewService#submitReview} for any request-level validation
 *  failure that would otherwise surface as a raw DB constraint violation
 *  or a NullPointerException - e.g. an unknown customer/service/skill id,
 *  a customer id that belongs to a non-customer user, a bad time range,
 *  or a rating outside 1-5. Mapped to HTTP 400 by
 *  {@code GlobalExceptionHandler}. */
public class BookingValidationException extends RuntimeException {
    public BookingValidationException(String message) {
        super(message);
    }
}
