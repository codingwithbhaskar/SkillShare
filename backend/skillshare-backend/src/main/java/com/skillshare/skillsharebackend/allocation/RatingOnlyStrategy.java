package com.skillshare.skillsharebackend.allocation;

import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.domain.WorkerStats;
import com.skillshare.skillsharebackend.repository.WorkerRepository;
import com.skillshare.skillsharebackend.repository.WorkerStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Phase 10 (Step 11) baseline arm #3 — highest-rated worker wins, ignoring
 * distance/price/workload/experience entirely. Same shared candidate pool
 * as {@link RandomAllocationStrategy} (see its javadoc).
 *
 * <p>Reads {@code avg_rating} straight from {@code mv_worker_stats} (via
 * {@link WorkerStatsRepository}) — the same materialized view {@code
 * fn_score_candidate} itself reads, so a worker with zero reviews compares
 * as {@code 0} here (COALESCE'd in the view definition, 01_schema_v3.sql),
 * not {@code null}. This class does NOT need to reimplement
 * fn_score_candidate's 0 -> 0.6 cold-start substitution — that's a scoring
 * *normalization* concern for comparing across strategies (handled once,
 * centrally, wherever candidates get scored for the AMS metric), not a
 * *selection* concern: "prefer the highest raw rating, ties broken by
 * distance" is well-defined on the raw COALESCE'd-to-0 value alone.
 */
@Service("ratingOnlyAllocator")
@RequiredArgsConstructor
public class RatingOnlyStrategy implements AllocationStrategy {

    private final WorkerRepository workerRepository;
    private final WorkerStatsRepository workerStatsRepository;

    @Override
    public Optional<Worker> selectWorker(Booking booking) {
        List<Long> candidateIds = workerRepository.findCandidateWorkerIds(booking.getBookingId());
        if (candidateIds.isEmpty()) {
            return Optional.empty();
        }

        Worker best = null;
        BigDecimal bestRating = null;
        double bestDistanceKmForTie = Double.MAX_VALUE;

        for (Long candidateId : candidateIds) {
            Worker candidate = workerRepository.findById(candidateId).orElse(null);
            if (candidate == null || candidate.getLocation() == null) {
                continue;
            }
            WorkerStats stats = workerStatsRepository.findById(candidateId).orElse(null);
            BigDecimal rating = (stats == null || stats.getAvgRating() == null)
                    ? BigDecimal.ZERO : stats.getAvgRating();

            double distanceKm = GeoUtils.haversineKm(
                    candidate.getLocation().getLatitude(), candidate.getLocation().getLongitude(),
                    booking.getLocation().getLatitude(), booking.getLocation().getLongitude());

            boolean better = bestRating == null
                    || rating.compareTo(bestRating) > 0
                    || (rating.compareTo(bestRating) == 0 && distanceKm < bestDistanceKmForTie);

            if (better) {
                best = candidate;
                bestRating = rating;
                bestDistanceKmForTie = distanceKm;
            }
        }
        return Optional.ofNullable(best);
    }
}
