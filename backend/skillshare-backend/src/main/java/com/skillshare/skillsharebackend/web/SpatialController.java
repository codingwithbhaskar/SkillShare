package com.skillshare.skillsharebackend.web;

import com.skillshare.skillsharebackend.allocation.SpatialSearchService;
import com.skillshare.skillsharebackend.web.dto.CandidateWorkerResponse;
import com.skillshare.skillsharebackend.web.dto.NearbyWorkerResponse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Phase 3 — the first REST surface in the project (Phase 2 was domain +
 * repository + allocator only, no HTTP endpoints yet). Deliberately thin:
 * all the actual logic lives in {@link SpatialSearchService}, which is what
 * gets unit-tested; this class only handles HTTP concerns (parameter
 * validation, status codes).
 *
 * <p>{@code BookingNotFoundException} handling moved to
 * {@link GlobalExceptionHandler} in Phase 4, once {@code
 * AllocationController} needed the same mapping and a local handler here
 * would have meant duplicating it.
 */
@RestController
@RequestMapping("/api/spatial")
@RequiredArgsConstructor
@Validated
public class SpatialController {

    private final SpatialSearchService spatialSearchService;

    /**
     * General-purpose radius search, e.g.
     * {@code GET /api/spatial/workers/nearby?lat=19.997454&lon=73.789803&radiusKm=15}.
     * {@code radiusKm} defaults to 10 if omitted; capped at 100 to keep the
     * search sane (the GiST index on {@code locations.geom} keeps even a
     * 100km ST_DWithin scan fast, this cap is about the results making
     * sense for a "nearby" search, not query performance).
     */
    @GetMapping("/workers/nearby")
    public List<NearbyWorkerResponse> nearbyWorkers(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") BigDecimal lon,
            @RequestParam(defaultValue = "10.0") @DecimalMin("0.1") @DecimalMax("100.0") double radiusKm) {
        return spatialSearchService.findNearbyWorkers(lat, lon, radiusKm);
    }

    /**
     * Preview of fn_find_candidates' hard-filtered candidate set for an
     * existing booking, without actually running sp_allocate_worker - e.g.
     * {@code GET /api/spatial/bookings/4/candidates}.
     */
    @GetMapping("/bookings/{bookingId}/candidates")
    public List<CandidateWorkerResponse> candidatesForBooking(@PathVariable Long bookingId) {
        return spatialSearchService.findCandidatesForBooking(bookingId);
    }
}
