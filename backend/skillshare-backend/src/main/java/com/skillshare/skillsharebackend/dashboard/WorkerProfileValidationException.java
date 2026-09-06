package com.skillshare.skillsharebackend.dashboard;

/** Thrown by {@code WorkerDashboardService#updateMyProfile} for a bad
 *  {@code PUT /api/workers/me} request (unknown skill/service id, an
 *  invalid availability window). Mapped to HTTP 400 by
 *  {@code GlobalExceptionHandler}, same status
 *  {@code BookingValidationException} maps to for the equivalent case on
 *  the booking side. */
public class WorkerProfileValidationException extends RuntimeException {
    public WorkerProfileValidationException(String message) {
        super(message);
    }
}
