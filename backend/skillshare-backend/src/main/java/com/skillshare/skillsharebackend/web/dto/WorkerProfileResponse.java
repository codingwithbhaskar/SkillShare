package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.Location;
import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/** Response shape for {@code GET /api/workers/me} and
 *  {@code PUT /api/workers/me} - closes the Phase 7 documented gap: a
 *  logged-in worker previously had no way to discover their own {@code
 *  workerId} in order to call the existing
 *  {@code GET /api/workers/{id}/bookings|reviews|stats} endpoints.
 *  {@code hasLocation} tells the frontend whether the map feature's
 *  Option A (static registered location) is usable yet.
 *
 * <p>The location/skillIds/serviceRates/availability fields were added
 * alongside {@code PUT /api/workers/me} (the "complete my profile"
 * follow-up) so the profile-edit form can show what's already on file
 * instead of always starting from a blank form - see
 * {@code UpdateWorkerProfileRequest}'s javadoc for why all four are more
 * than cosmetic (each is a hard filter in {@code fn_find_candidates}). */
public record WorkerProfileResponse(
        Long workerId,
        Long userId,
        String fullName,
        String bio,
        Short experienceYears,
        BigDecimal baseHourlyRate,
        AccountStatus status,
        boolean hasLocation,
        String addressLine,
        String city,
        String state,
        String pincode,
        BigDecimal latitude,
        BigDecimal longitude,
        List<Long> skillIds,
        List<ServiceRateView> serviceRates,
        List<AvailabilityWindowView> availability) {

    /** Mirrors {@code UpdateWorkerProfileRequest.ServiceRate} on the read
     *  side - kept as a separate record (not reused directly) since a
     *  request DTO and a response DTO drifting apart independently is
     *  normal and expected, even though the shapes happen to match today. */
    public record ServiceRateView(Long serviceId, BigDecimal hourlyRate) {
    }

    /** Mirrors {@code UpdateWorkerProfileRequest.AvailabilityWindow} on
     *  the read side - same independent-DTO reasoning as
     *  {@link ServiceRateView}. */
    public record AvailabilityWindowView(Short dayOfWeek, LocalTime startTime, LocalTime endTime) {
    }

    /** Minimal form - {@code skillIds}/{@code serviceRates}/{@code
     *  availability} empty. Not currently used by {@code
     *  WorkerDashboardService} (both its callers now have the extra
     *  repository lookups on hand and use the full factory below), kept
     *  as a convenience for any future caller that only needs the basic
     *  fields and wants to skip the extra queries. */
    public static WorkerProfileResponse from(Worker worker) {
        return from(worker, List.of(), List.of(), List.of());
    }

    public static WorkerProfileResponse from(Worker worker, List<Long> skillIds,
            List<ServiceRateView> serviceRates, List<AvailabilityWindowView> availability) {
        Location location = worker.getLocation();
        return new WorkerProfileResponse(
                worker.getWorkerId(),
                worker.getUser().getUserId(),
                worker.getUser().getFullName(),
                worker.getBio(),
                worker.getExperienceYears(),
                worker.getBaseHourlyRate(),
                worker.getStatus(),
                location != null,
                location != null ? location.getAddressLine() : null,
                location != null ? location.getCity() : null,
                location != null ? location.getState() : null,
                location != null ? location.getPincode() : null,
                location != null ? location.getLatitude() : null,
                location != null ? location.getLongitude() : null,
                skillIds,
                serviceRates,
                availability);
    }
}
