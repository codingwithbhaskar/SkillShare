package com.skillshare.skillsharebackend.booking;

import com.skillshare.skillsharebackend.domain.enums.BookingStatus;

/** Thrown by {@link BookingService}'s cancel/start/complete methods when
 *  a booking exists but isn't in the status that transition requires
 *  (e.g. calling /start on a booking that's still pending, not yet
 *  confirmed). Mapped to HTTP 409 by {@code GlobalExceptionHandler} -
 *  same status code {@link com.skillshare.skillsharebackend.allocation.
 *  BookingNotAllocatableException} uses for the analogous guard in
 *  AllocationService. */
public class InvalidBookingTransitionException extends RuntimeException {
    public InvalidBookingTransitionException(Long bookingId, BookingStatus currentStatus, String action,
            String requiredStatusDescription) {
        super("Booking " + bookingId + " cannot " + action + " - current status is " + currentStatus
                + ", requires " + requiredStatusDescription);
    }
}
