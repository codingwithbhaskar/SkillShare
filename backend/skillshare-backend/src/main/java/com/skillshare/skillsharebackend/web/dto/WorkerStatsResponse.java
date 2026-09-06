package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.WorkerStats;

import java.math.BigDecimal;

/** Response shape for {@code GET /api/workers/{id}/stats}, wrapping
 *  {@code mv_worker_stats}. {@link #zero} is the fallback for a real
 *  worker whose row hasn't appeared in the materialized view yet - it's
 *  refreshed only via {@code REFRESH MATERIALIZED VIEW CONCURRENTLY}, not
 *  automatically on every booking/review, so a worker created after the
 *  last refresh legitimately has no row there yet. */
public record WorkerStatsResponse(
        Long workerId,
        Long totalBookings,
        Long completedBookings,
        Long activeBookings,
        Long reviewCount,
        BigDecimal avgRating) {

    public static WorkerStatsResponse from(WorkerStats stats) {
        return new WorkerStatsResponse(
                stats.getWorkerId(),
                stats.getTotalBookings(),
                stats.getCompletedBookings(),
                stats.getActiveBookings(),
                stats.getReviewCount(),
                stats.getAvgRating());
    }

    public static WorkerStatsResponse zero(Long workerId) {
        return new WorkerStatsResponse(workerId, 0L, 0L, 0L, 0L, BigDecimal.ZERO);
    }
}
