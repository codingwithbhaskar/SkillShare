package com.skillshare.skillsharebackend.allocation;

import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.Worker;

import java.util.Optional;

/**
 * Common contract for anything that picks a worker for a booking. Phase 2
 * ships one implementation ({@link TraditionalAllocator}) — the "dumb"
 * baseline (skill filter -> availability filter -> nearest worker, all in
 * Java) that Phase 9's evaluation compares the PL/pgSQL intelligent
 * allocator (sp_allocate_worker, wired up in Phase 4) against. Both sides
 * of that comparison implement this same interface so they're swappable.
 */
public interface AllocationStrategy {

    /**
     * @param booking a persisted, unassigned booking (must already have a
     *                bookingId — used to exclude itself when checking for
     *                schedule conflicts, and requires booking_skills rows
     *                to already exist if the booking needs specific
     *                skills).
     * @return the chosen worker, or empty if no worker satisfies every
     *         filter.
     */
    Optional<Worker> selectWorker(Booking booking);
}
