package com.skillshare.skillsharebackend.allocation;

import com.skillshare.skillsharebackend.repository.AllocationScoreProjection;
import com.skillshare.skillsharebackend.repository.BookingRepository;
import com.skillshare.skillsharebackend.repository.WorkerRepository;
import com.skillshare.skillsharebackend.web.dto.CandidateScoreResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Phase 4 — thin Java wrapper over {@code fn_score_candidate}
 * (05_functions_procedures_v3.sql), the same weighted 6-factor scoring
 * function {@code sp_allocate_worker} itself calls internally for every
 * candidate before picking the best one. No scoring math is reimplemented
 * here — consistent with this project's standing architectural principle
 * that allocation-critical logic lives in PL/pgSQL, not Java (see
 * fn_score_candidate's own verified behavior, including the soft skill-
 * match scoring and cold-start rating default, in
 * schema-v3-triggers-functions.md).
 */
@Service
@RequiredArgsConstructor
public class AllocationScoringService {

    private final WorkerRepository workerRepository;
    private final BookingRepository bookingRepository;

    /**
     * Scores one specific worker as a candidate for one specific booking.
     * Only checks that the booking and worker each exist — NOT that the
     * worker is an eligible candidate for this booking (i.e. would appear
     * in fn_find_candidates). fn_score_candidate itself doesn't hard-
     * filter by eligibility either; it happily scores anyone real. A
     * worker who exists but doesn't offer the booking's service will come
     * back with {@code priceScore}/{@code totalScore} as {@code null}
     * (see {@link WorkerNotFoundException}'s javadoc) — that's expected
     * pass-through behavior, not a validity check this method performs.
     */
    public CandidateScoreResponse scoreCandidate(Long bookingId, Long workerId) {
        if (!bookingRepository.existsById(bookingId)) {
            throw new BookingNotFoundException(bookingId);
        }
        if (!workerRepository.existsById(workerId)) {
            throw new WorkerNotFoundException(workerId);
        }
        return toResponse(workerId, workerRepository.scoreCandidate(bookingId, workerId));
    }

    /**
     * Scores every hard-filtered candidate for a booking (the same set
     * fn_find_candidates/sp_allocate_worker itself considers), sorted
     * best-score-first — a read-only preview of what allocating would
     * decide, useful for demoing/verifying the allocation logic without
     * actually committing an allocation.
     */
    public List<CandidateScoreResponse> scoreAllCandidates(Long bookingId) {
        if (!bookingRepository.existsById(bookingId)) {
            throw new BookingNotFoundException(bookingId);
        }
        return workerRepository.findCandidateWorkerIds(bookingId).stream()
                .map(workerId -> toResponse(workerId, workerRepository.scoreCandidate(bookingId, workerId)))
                .sorted(Comparator.comparing(CandidateScoreResponse::totalScore).reversed())
                .toList();
    }

    private CandidateScoreResponse toResponse(Long workerId, AllocationScoreProjection p) {
        return new CandidateScoreResponse(
                workerId, p.getSkillScore(), p.getRatingScore(), p.getDistanceScore(),
                p.getWorkloadScore(), p.getPriceScore(), p.getExperienceScore(), p.getTotalScore());
    }
}
