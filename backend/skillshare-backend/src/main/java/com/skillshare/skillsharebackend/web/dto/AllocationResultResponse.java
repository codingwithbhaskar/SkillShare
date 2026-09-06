package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.enums.BookingStatus;

/**
 * Outcome of a {@code POST /api/allocation/bookings/{id}/allocate} call.
 * {@code sp_allocate_worker} itself gives its JDBC caller no direct
 * success/failure signal either way — a failed allocation (no eligible
 * worker left after all exclusion-violation retries) is only a
 * {@code RAISE NOTICE} inside the procedure body, which plain JDBC never
 * surfaces as a Java-visible event. {@code allocated}/{@code status}/
 * {@code assignedWorkerId} are all derived by
 * {@link com.skillshare.skillsharebackend.allocation.AllocationService}
 * re-querying the booking's own row immediately after the CALL, in the
 * same transaction.
 */
public record AllocationResultResponse(
        Long bookingId,
        boolean allocated,
        BookingStatus status,
        Long assignedWorkerId) {
}
