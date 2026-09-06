package com.skillshare.skillsharebackend.allocation;

import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.repository.AllocationScoreProjection;
import com.skillshare.skillsharebackend.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Phase 10 (Step 11) — a read-only {@link AllocationStrategy} view of the
 * SAME logic {@code sp_allocate_worker} uses (fn_find_candidates +
 * fn_score_candidate, best-score-first), for the evaluation harness to
 * compare against the four baseline arms and {@link TraditionalAllocator}
 * on equal footing.
 *
 * <p>Deliberately NOT the same class as {@link
 * com.skillshare.skillsharebackend.allocation.AllocationService} (the
 * real production allocation path): that class CALLs {@code
 * sp_allocate_worker}, which commits the assignment, re-checks pending
 * status under a row lock, and refreshes {@code mv_worker_stats} - real
 * side effects appropriate for actually allocating a booking, wrong for
 * evaluating six strategies against the same fixed batch of bookings
 * without one strategy's picks changing the world the next strategy sees.
 * This class only ever reads: it reuses {@link WorkerRepository}'s
 * existing thin wrappers over fn_find_candidates/fn_score_candidate (the
 * same ones {@link
 * com.skillshare.skillsharebackend.allocation.AllocationScoringService}
 * already exposes to the candidate-preview endpoint) and picks the
 * highest {@code total_score}, mirroring sp_allocate_worker's own
 * best-first ordering exactly - it just stops at "pick", it never
 * attempts the retry-on-conflict INSERT sp_allocate_worker's PL/pgSQL body
 * does, since nothing is being committed here for a conflict to occur
 * against.
 */
@Service("intelligentAllocator")
@RequiredArgsConstructor
public class IntelligentAllocationStrategy implements AllocationStrategy {

    private final WorkerRepository workerRepository;

    @Override
    public Optional<Worker> selectWorker(Booking booking) {
        List<Long> candidateIds = workerRepository.findCandidateWorkerIds(booking.getBookingId());
        if (candidateIds.isEmpty()) {
            return Optional.empty();
        }

        Long bestWorkerId = null;
        BigDecimal bestScore = null;
        for (Long candidateId : candidateIds) {
            AllocationScoreProjection score = workerRepository.scoreCandidate(booking.getBookingId(), candidateId);
            BigDecimal total = score.getTotalScore();
            if (total != null && (bestScore == null || total.compareTo(bestScore) > 0)) {
                bestScore = total;
                bestWorkerId = candidateId;
            }
        }
        return bestWorkerId == null ? Optional.empty() : workerRepository.findById(bestWorkerId);
    }
}
