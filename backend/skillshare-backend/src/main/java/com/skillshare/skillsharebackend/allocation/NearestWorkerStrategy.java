package com.skillshare.skillsharebackend.allocation;

import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Phase 10 (Step 11) baseline arm #2 — "nearest worker wins", the single
 * most common naive allocation heuristic in the literature. Same shared
 * candidate pool as {@link RandomAllocationStrategy} (see its javadoc for
 * why holding the pool constant matters), but the SELECTION rule is pure
 * proximity: minimum Haversine distance to the booking's location, ignoring
 * rating, price, workload, and experience entirely.
 *
 * <p>Distinct from {@link TraditionalAllocator} (also nearest-by-distance)
 * in two ways: this draws from fn_find_candidates' pool (30km radius hard
 * cap, soft skill match) rather than TraditionalAllocator's own JPA-built
 * pool (no radius cap at all, hard skill match) — see
 * TraditionalAllocator's javadoc. Keeping both gives the write-up two
 * different "dumb nearest-worker" baselines to compare against the smart
 * allocator, not just one.
 */
@Service("nearestWorkerAllocator")
@RequiredArgsConstructor
public class NearestWorkerStrategy implements AllocationStrategy {

    private final WorkerRepository workerRepository;

    @Override
    public Optional<Worker> selectWorker(Booking booking) {
        List<Long> candidateIds = workerRepository.findCandidateWorkerIds(booking.getBookingId());
        if (candidateIds.isEmpty()) {
            return Optional.empty();
        }

        Worker best = null;
        double bestDistanceKm = Double.MAX_VALUE;
        for (Long candidateId : candidateIds) {
            Worker candidate = workerRepository.findById(candidateId).orElse(null);
            if (candidate == null || candidate.getLocation() == null) {
                continue;
            }
            double distanceKm = GeoUtils.haversineKm(
                    candidate.getLocation().getLatitude(), candidate.getLocation().getLongitude(),
                    booking.getLocation().getLatitude(), booking.getLocation().getLongitude());
            if (distanceKm < bestDistanceKm) {
                bestDistanceKm = distanceKm;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }
}
