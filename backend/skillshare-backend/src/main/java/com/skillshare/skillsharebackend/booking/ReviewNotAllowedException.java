package com.skillshare.skillsharebackend.booking;

/** Thrown by {@link ReviewService#submitReview} when a booking isn't yet
 *  eligible for a review - not {@code completed}, or a review already
 *  exists for it ({@code reviews.booking_id} is UNIQUE at the DB level;
 *  this check runs first so the caller gets a clear 409 instead of a raw
 *  constraint-violation stack trace). Mapped to HTTP 409 by
 *  {@code GlobalExceptionHandler}. */
public class ReviewNotAllowedException extends RuntimeException {
    public ReviewNotAllowedException(Long bookingId, String reason) {
        super("Booking " + bookingId + " cannot be reviewed - " + reason);
    }
}
