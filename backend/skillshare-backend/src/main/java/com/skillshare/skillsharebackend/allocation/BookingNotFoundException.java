package com.skillshare.skillsharebackend.allocation;

/** Thrown by {@link SpatialSearchService} when a candidate lookup is
 *  requested for a booking ID that doesn't exist. Mapped to HTTP 404 by
 *  {@code SpatialController}. */
public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(Long bookingId) {
        super("No booking found with id " + bookingId);
    }
}
