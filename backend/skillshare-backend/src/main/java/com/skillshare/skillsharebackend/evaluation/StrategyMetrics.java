package com.skillshare.skillsharebackend.evaluation;

/**
 * Phase 10 (Step 11) — one strategy's results from one
 * {@link AllocationEvaluationService} run, over the SAME fixed batch of
 * synthetic bookings every other strategy in the same run also saw
 * (nothing here is committed to the database between strategies - see
 * {@link com.skillshare.skillsharebackend.allocation.IntelligentAllocationStrategy}'s
 * javadoc on why - so this is a fair, order-independent comparison).
 *
 * <ul>
 *   <li><b>assignmentSuccessRate (ASR)</b> - successfulAssignments / totalBookings.</li>
 *   <li><b>avgMatchingScore (AMS)</b> - the intelligent allocator's own
 *       fn_score_candidate total_score (0-1) for whichever worker THIS
 *       strategy picked, averaged over successes. Scored on the SAME
 *       yardstick regardless of which strategy made the pick, so e.g.
 *       RandomAllocationStrategy's picks are graded by the intelligent
 *       criteria too - this is what makes AMS comparable across rows.</li>
 *   <li><b>avgTravelDistanceKm (ATD)</b> - Haversine distance between the
 *       picked worker and the booking location, averaged over successes.</li>
 *   <li><b>avgResponseTimeMs (ART)</b> - wall-clock time inside {@code
 *       selectWorker(...)} itself, averaged over ALL bookings (including
 *       failures) - the algorithmic cost of the strategy, not a real
 *       network/dispatch latency.</li>
 *   <li><b>workerUtilization (WU)</b> - distinctWorkersUsed /
 *       eligibleWorkerPoolSize, where the denominator is the union of
 *       every worker who appeared in ANY booking's fn_find_candidates
 *       result across the whole batch (shared across all 6 rows for a
 *       consistent scale) - a load-spread proxy: does this strategy keep
 *       funneling work to the same few "best" workers, or actually use
 *       the pool it has? Note {@code TraditionalAllocator} draws from its
 *       own, structurally different (uncapped-radius) candidate pool - see
 *       its javadoc - so its own numerator can include workers outside
 *       this shared denominator's pool; that's flagged, not hidden.</li>
 *   <li><b>customerSatisfactionProxy</b> - NOT measured from real customer
 *       reviews (these are hypothetical, never-happened allocations - no
 *       customer ever experienced them). A documented proxy only:
 *       0.7 * AMS + 0.3 * max(0, 1 - ATD/30km). Report this as an
 *       illustrative combination of match quality and proximity, not as
 *       validated customer sentiment.</li>
 * </ul>
 */
public record StrategyMetrics(
        String strategyKey,
        int totalBookings,
        int successfulAssignments,
        double assignmentSuccessRate,
        double avgMatchingScore,
        double avgTravelDistanceKm,
        double avgResponseTimeMs,
        int distinctWorkersUsed,
        int eligibleWorkerPoolSize,
        double workerUtilization,
        double customerSatisfactionProxy) {
}
