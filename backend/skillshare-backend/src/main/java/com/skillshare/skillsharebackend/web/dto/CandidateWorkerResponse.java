package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.enums.AccountStatus;

import java.math.BigDecimal;

/** Response shape for {@code GET /api/spatial/bookings/{bookingId}/candidates}.
 *  Deliberately only direct, eagerly-loaded columns off {@code Worker} -
 *  no lazy {@code user}/{@code location} association is touched here, so
 *  this stays safe to build outside a transaction. */
public record CandidateWorkerResponse(
        Long workerId,
        Short experienceYears,
        BigDecimal baseHourlyRate,
        AccountStatus status) {
}
