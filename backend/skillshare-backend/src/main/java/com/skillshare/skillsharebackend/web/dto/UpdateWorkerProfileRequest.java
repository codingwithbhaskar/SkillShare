package com.skillshare.skillsharebackend.web.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Request body for {@code PUT /api/workers/me} - closes the "no
 * self-service complete-my-profile" gap documented since Phase 7
 * (dev-status-and-next-steps.md) and repeated in
 * phase-8-frontend-in-progress.md's "still explicitly out of scope"
 * section: registration creates a bare {@code workers} row with no
 * location, no skills, no service offerings, and no availability, and
 * every one of those is a HARD filter in {@code fn_find_candidates} -
 * without this endpoint a newly-registered worker could never actually
 * be allocated a booking no matter how the frontend looked.
 *
 * <p>Partial-update semantics: {@code bio}/{@code experienceYears}/
 * {@code baseHourlyRate} are set whenever non-null; the location fields
 * are applied as a group whenever {@code addressLine} is non-null (a
 * fresh {@code Location} row is created and re-pointed at, same pattern
 * {@code BookingService#createBooking} already uses for job addresses -
 * see that method's javadoc for why locations are never mutated in
 * place); {@code skillIds}/{@code serviceRates}/{@code availability} each
 * REPLACE that worker's entire existing set whenever the list is
 * non-null (an empty list clears it, {@code null} leaves it untouched) -
 * simpler and safer than diffing individual rows for a form that always
 * submits its complete current state.
 */
public record UpdateWorkerProfileRequest(
        String bio,
        Short experienceYears,
        BigDecimal baseHourlyRate,
        String addressLine,
        String landmark,
        String city,
        String state,
        String pincode,
        BigDecimal latitude,
        BigDecimal longitude,
        List<Long> skillIds,
        List<ServiceRate> serviceRates,
        List<AvailabilityWindow> availability) {

    /** One service this worker offers, and the rate {@code
     *  fn_score_candidate}'s price-fit factor actually reads
     *  ({@code worker_services.hourly_rate} - NOT {@code
     *  workers.base_hourly_rate}, which the scorer never looks at). */
    public record ServiceRate(Long serviceId, BigDecimal hourlyRate) {
    }

    /** One weekly availability window - {@code dayOfWeek} follows
     *  Postgres' {@code EXTRACT(DOW ...)} convention (0 = Sunday ... 6 =
     *  Saturday), matching {@code WorkerAvailability}'s own javadoc.
     *  {@code fn_find_candidates} hard-requires at least one matching row
     *  covering a booking's requested day/time, so a worker with an
     *  empty availability list is simply never eligible for anything. */
    public record AvailabilityWindow(Short dayOfWeek, LocalTime startTime, LocalTime endTime) {
    }
}
