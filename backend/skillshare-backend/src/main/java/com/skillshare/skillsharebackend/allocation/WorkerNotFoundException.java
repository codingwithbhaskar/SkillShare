package com.skillshare.skillsharebackend.allocation;

/**
 * Thrown by {@link AllocationScoringService#scoreCandidate} when the given
 * worker ID doesn't exist at all. Mapped to HTTP 404 by
 * {@code GlobalExceptionHandler}.
 *
 * <p>Distinct from a worker who exists but isn't actually eligible for the
 * booking (doesn't offer the service, is inactive, etc.) — that case is
 * NOT an error: {@link AllocationScoringService#scoreCandidate} is
 * documented to score any real worker regardless of eligibility, matching
 * fn_score_candidate's own behavior (it doesn't hard-filter by
 * eligibility either). A worker who exists but doesn't offer the
 * booking's service will come back with {@code priceScore}/{@code
 * totalScore} as {@code null} (Postgres arithmetic against a rate that
 * doesn't exist propagates SQL NULL) rather than throwing — that's
 * expected, not a bug. This exception only covers the "no such worker row
 * at all" case, which fn_score_candidate can't meaningfully score either.
 */
public class WorkerNotFoundException extends RuntimeException {
    public WorkerNotFoundException(Long workerId) {
        super("No worker found with id " + workerId);
    }
}
