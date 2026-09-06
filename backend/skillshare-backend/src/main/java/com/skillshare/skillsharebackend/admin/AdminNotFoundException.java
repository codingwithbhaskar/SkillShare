package com.skillshare.skillsharebackend.admin;

/** A referenced admin-managed resource (user, service, skill) doesn't
 *  exist. Mapped to 404 by GlobalExceptionHandler, same pattern as
 *  {@code WorkerNotFoundException}/{@code BookingNotFoundException} in
 *  the allocation package - a separate copy here rather than reusing
 *  those since this one isn't scoped to workers/bookings specifically. */
public class AdminNotFoundException extends RuntimeException {
    public AdminNotFoundException(String message) {
        super(message);
    }
}
