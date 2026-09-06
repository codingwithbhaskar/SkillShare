package com.skillshare.skillsharebackend.web.dto;

import java.math.BigDecimal;

/**
 * One {@code fn_score_candidate(...)} result (05_functions_procedures_v3.sql)
 * — the same six normalized (0-1) factors plus their weighted total that
 * sp_allocate_worker computes and logs into {@code allocation_log} for
 * every candidate it considers. No scoring math is redone in Java; every
 * field here is a direct pass-through from Postgres via
 * {@link com.skillshare.skillsharebackend.repository.AllocationScoreProjection}.
 */
public record CandidateScoreResponse(
        Long workerId,
        BigDecimal skillScore,
        BigDecimal ratingScore,
        BigDecimal distanceScore,
        BigDecimal workloadScore,
        BigDecimal priceScore,
        BigDecimal experienceScore,
        BigDecimal totalScore) {
}
