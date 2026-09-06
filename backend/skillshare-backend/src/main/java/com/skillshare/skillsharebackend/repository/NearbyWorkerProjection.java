package com.skillshare.skillsharebackend.repository;

/**
 * Spring Data interface-based projection for {@link
 * WorkerRepository#findWorkersWithinRadius}'s native query. Getter names
 * match the query's column aliases ({@code workerId}, {@code distanceKm})
 * case-insensitively, which is how Spring Data wires a native query's
 * result set to a projection interface without a full entity mapping.
 */
public interface NearbyWorkerProjection {
    Long getWorkerId();
    Double getDistanceKm();
}
