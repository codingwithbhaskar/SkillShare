package com.skillshare.skillsharebackend.allocation;

import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.domain.WorkerStats;
import com.skillshare.skillsharebackend.repository.WorkerRepository;
import com.skillshare.skillsharebackend.repository.WorkerStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Phase 10 (Step 11) baseline arm #4 — "the simple heuristic a team
 * without a DBMS specialist might ship": a 2-factor weighted score
 * (distance + rating only, 50/50), computed here in plain Java rather than
 * by calling fn_score_candidate. Same shared candidate pool as
 * {@link RandomAllocationStrategy} (see its javadoc).
 *
 * <p>Deliberately mirrors fn_score_candidate's own normalization formulas
 * for the two factors it DOES use (distance linearly scaled against the
 * 30km radius, rating as min(avg/5, 1) with the same 0 -> 0.6 cold-start
 * substitution) — so the only real difference from the full intelligent
 * allocator is which factors are considered (2 vs. 6: no skill, workload,
 * price, or experience weighting here) and the fixed 50/50 split rather
 * than the tuned {@code allocation_criteria} weights. That isolates
 * exactly what the write-up wants to measure: does the FULL 6-factor
 * weighted model out-perform a simpler 2-factor version of the same idea,
 * not just "does weighting anything beat not weighting at all" (that's
 * what the Random/Nearest/RatingOnly arms already cover).
 */
@Service("simpleWeightedAllocator")
@RequiredArgsConstructor
public class SimpleWeightedStrategy implements AllocationStrategy {

    /** Matches fn_find_candidates'/fn_score_candidate's hardcoded 30km
     *  search radius (05_functions_procedures_v3.sql) - see those
     *  functions' own comments on why it isn't yet promoted into
     *  allocation_criteria. */
    private static final double SEARCH_RADIUS_METERS = 30_000.0;
    private static final double DISTANCE_WEIGHT = 0.5;
    private static final double RATING_WEIGHT = 0.5;

    private final WorkerRepository workerRepository;
    private final WorkerStatsRepository workerStatsRepository;

    @Override
    public Optional<Worker> selectWorker(Booking booking) {
        List<Long> candidateIds = workerRepository.findCandidateWorkerIds(booking.getBookingId());
        if (candidateIds.isEmpty()) {
            return Optional.empty();
        }

        Worker best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Long candidateId : candidateIds) {
            Worker candidate = workerRepository.findById(candidateId).orElse(null);
            if (candidate == null || candidate.getLocation() == null) {
                continue;
            }

            double distanceKm = GeoUtils.haversineKm(
                    candidate.getLocation().getLatitude(), candidate.getLocation().getLongitude(),
                    booking.getLocation().getLatitude(), booking.getLocation().getLongitude());
            double distanceScore = Math.max(0.0, 1.0 - (distanceKm * 1000.0) / SEARCH_RADIUS_METERS);

            WorkerStats stats = workerStatsRepository.findById(candidateId).orElse(null);
            double rawRating = (stats == null || stats.getAvgRating() == null)
                    ? 0.0 : stats.getAvgRating().doubleValue();
            double ratingScore = (rawRating <= 0.0) ? 0.6 : Math.min(rawRating / 5.0, 1.0);

            double combined = (distanceScore * DISTANCE_WEIGHT) + (ratingScore * RATING_WEIGHT);
            if (combined > bestScore) {
                bestScore = combined;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }
}
