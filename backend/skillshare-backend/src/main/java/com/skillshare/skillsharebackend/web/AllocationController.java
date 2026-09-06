package com.skillshare.skillsharebackend.web;

import com.skillshare.skillsharebackend.allocation.AllocationScoringService;
import com.skillshare.skillsharebackend.allocation.AllocationService;
import com.skillshare.skillsharebackend.security.AuthenticatedUser;
import com.skillshare.skillsharebackend.web.dto.AllocationResultResponse;
import com.skillshare.skillsharebackend.web.dto.CandidateScoreResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phase 4 — the intelligent allocator's HTTP surface. Deliberately thin,
 * same split as {@link SpatialController}: all the actual logic lives in
 * {@link AllocationScoringService}/{@link AllocationService}, which is
 * what's unit-tested; this class only handles HTTP concerns.
 */
@RestController
@RequestMapping("/api/allocation")
@RequiredArgsConstructor
public class AllocationController {

    private final AllocationScoringService allocationScoringService;
    private final AllocationService allocationService;

    /**
     * Scores one specific candidate against one booking, without touching
     * allocation state at all — e.g.
     * {@code GET /api/allocation/bookings/4/candidates/1/score}. 404 if
     * either the booking or the worker doesn't exist. Does NOT check that
     * the worker is actually eligible for this booking — a worker who
     * exists but doesn't offer the booking's service is still scored, and
     * comes back with a {@code null} price/total score rather than an
     * error (see {@code WorkerNotFoundException}'s javadoc).
     */
    @GetMapping("/bookings/{bookingId}/candidates/{workerId}/score")
    public CandidateScoreResponse scoreCandidate(@PathVariable Long bookingId, @PathVariable Long workerId) {
        return allocationScoringService.scoreCandidate(bookingId, workerId);
    }

    /**
     * Scores every hard-filtered candidate for a booking (the exact set
     * sp_allocate_worker itself would consider), sorted best-score-first —
     * a read-only preview of what allocating would decide, without
     * actually committing it. E.g.
     * {@code GET /api/allocation/bookings/4/candidates/scores}.
     */
    @GetMapping("/bookings/{bookingId}/candidates/scores")
    public List<CandidateScoreResponse> scoreAllCandidates(@PathVariable Long bookingId) {
        return allocationScoringService.scoreAllCandidates(bookingId);
    }

    /**
     * Runs the real allocation: locks the booking, scores every candidate,
     * assigns the best available one, fires the worker-assigned
     * notifications — e.g. {@code POST /api/allocation/bookings/4/allocate}.
     * Mutating, so POST rather than GET, unlike every Phase 3 endpoint.
     */
    @PostMapping("/bookings/{bookingId}/allocate")
    public AllocationResultResponse allocate(@PathVariable Long bookingId, Authentication authentication) {
        return allocationService.allocate(bookingId, AuthenticatedUser.from(authentication));
    }
}
