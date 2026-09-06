package com.skillshare.skillsharebackend.allocation;

import com.skillshare.skillsharebackend.domain.enums.BookingStatus;

/**
 * Thrown by {@link AllocationService#allocate} when a booking exists but
 * is not currently {@code pending}. {@code sp_allocate_worker} itself has
 * no concept of "already allocated" — called again on an already-confirmed
 * booking, it will happily re-lock the row, re-run fn_find_candidates/
 * fn_score_candidate from scratch, and overwrite the booking's worker_id
 * with whatever scores best *this time* (possibly a different worker than
 * the one originally assigned, since candidate scores like workload and
 * distance can have changed since the first call). The stored procedure
 * doesn't guard against this — see 05_functions_procedures_v3.sql, it
 * simply isn't its job — so the Java layer owns this check instead.
 * Mapped to HTTP 409 by {@code GlobalExceptionHandler}.
 */
public class BookingNotAllocatableException extends RuntimeException {
    public BookingNotAllocatableException(Long bookingId, BookingStatus currentStatus) {
        super("Booking " + bookingId + " cannot be allocated - current status is "
                + currentStatus + ", not pending");
    }
}
