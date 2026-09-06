package com.skillshare.skillsharebackend.web.dto;

/** Response shape for {@code GET /api/spatial/workers/nearby}. */
public record NearbyWorkerResponse(Long workerId, double distanceKm) {
}
