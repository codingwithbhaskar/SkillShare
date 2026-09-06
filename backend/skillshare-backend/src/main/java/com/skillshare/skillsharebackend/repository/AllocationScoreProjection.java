package com.skillshare.skillsharebackend.repository;

import java.math.BigDecimal;

/**
 * Projection for {@code fn_score_candidate(p_booking_id, p_worker_id)}
 * (05_functions_procedures_v3.sql) — the six normalized (0-1) scoring
 * factors sp_allocate_worker itself computes for every candidate, plus
 * their weighted total. The wrapping native query explicitly aliases each
 * column (skill_score AS skillScore, etc.) rather than relying on Spring
 * Data's automatic snake_case-to-camelCase projection matching, same
 * convention {@link NearbyWorkerProjection} already established in
 * Phase 3.
 */
public interface AllocationScoreProjection {
    BigDecimal getSkillScore();
    BigDecimal getRatingScore();
    BigDecimal getDistanceScore();
    BigDecimal getWorkloadScore();
    BigDecimal getPriceScore();
    BigDecimal getExperienceScore();
    BigDecimal getTotalScore();
}
