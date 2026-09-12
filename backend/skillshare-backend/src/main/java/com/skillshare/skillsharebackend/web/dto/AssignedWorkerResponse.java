package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.domain.WorkerStats;

import java.math.BigDecimal;

/**
 * Customer/worker-facing summary of the worker assigned to a booking -
 * embedded in {@link BookingResponse} once one has been allocated.
 * Closes a real gap: the frontend previously only had the bare {@code
 * workerId} to show on a booking, so a customer had no way to see WHO
 * was actually coming to do the job.
 *
 * <p>Deliberately exposes only contact/professional-trust fields (name,
 * phone, bio, experience, rate, rating) - never the worker's email or
 * account status, which aren't this viewer's business (same principle as
 * {@code UpdateUserStatusRequest}'s "editing identity fields is out of
 * scope" javadoc).
 */
public record AssignedWorkerResponse(
        Long workerId,
        String fullName,
        String phone,
        String bio,
        Short experienceYears,
        BigDecimal baseHourlyRate,
        BigDecimal avgRating,
        Long reviewCount) {

    /** Basic form - no {@code mv_worker_stats} lookup, so no rating.
     *  Used by the write-path {@code BookingResponse} factories
     *  (create/cancel/start/complete), which don't have a repository on
     *  hand in their static context; the frontend re-fetches via
     *  {@code GET /api/bookings/{id}} right after every action anyway,
     *  which uses {@link #from(Worker, WorkerStats)} instead. */
    public static AssignedWorkerResponse from(Worker worker) {
        return from(worker, null);
    }

    /** {@code stats} is null-safe - a brand-new worker with zero
     *  completed jobs has no {@code mv_worker_stats} row yet. */
    public static AssignedWorkerResponse from(Worker worker, WorkerStats stats) {
        return new AssignedWorkerResponse(
                worker.getWorkerId(),
                worker.getUser().getFullName(),
                worker.getUser().getPhone(),
                worker.getBio(),
                worker.getExperienceYears(),
                worker.getBaseHourlyRate(),
                stats != null ? stats.getAvgRating() : null,
                stats != null ? stats.getReviewCount() : null);
    }
}
