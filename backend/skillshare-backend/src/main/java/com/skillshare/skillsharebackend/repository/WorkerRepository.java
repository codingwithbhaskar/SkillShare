package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {
    Optional<Worker> findByUser_UserId(Long userId);
    List<Worker> findByStatus(AccountStatus status);

    /**
     * Wraps `fn_find_candidates(p_booking_id)` (05_functions_procedures_v3.sql)
     * — the same hard-filter candidate retrieval `sp_allocate_worker` uses
     * internally (active status, offers the service, available at the
     * requested window, within the 30km radius via PostGIS ST_DWithin, no
     * overlapping booking). Deliberately a thin pass-through: the allocation-
     * critical filtering logic stays in PL/pgSQL, per this project's
     * standing architectural principle — this just exposes it to Java for
     * Phase 3's candidate-preview endpoint, it doesn't reimplement it.
     *
     * Native query rather than {@code @Procedure}: fn_find_candidates is a
     * PostgreSQL function returning {@code TABLE(worker_id BIGINT)} (a set,
     * not OUT params), which native SQL + a scalar return type handles more
     * directly than JPA's {@code @Procedure} annotation.
     */
    @Query(value = "SELECT worker_id FROM fn_find_candidates(:bookingId)", nativeQuery = true)
    List<Long> findCandidateWorkerIds(@Param("bookingId") Long bookingId);

    /**
     * General-purpose "workers near this point" radius search, independent
     * of any specific booking — the Phase 3 roadmap item distinct from
     * {@link #findCandidateWorkerIds}. Uses the same {@code geography}
     * column and {@code ST_DWithin}/{@code ST_Distance} PostGIS functions as
     * fn_find_candidates and fn_score_candidate, just parameterized by an
     * arbitrary lat/long instead of an existing booking's location.
     *
     * Deliberately native SQL rather than a Hibernate Spatial JTS-typed
     * field on {@code Location.geom}: Hibernate's own spatial documentation
     * doesn't cover {@code geography} columns (as opposed to {@code
     * geometry}), and {@code geom} is populated entirely by the
     * fn_sync_location_geom trigger — the app never needs to read or write
     * it as a Java object, only ask Postgres questions about it. Passing
     * lat/lon as plain bind parameters and letting Postgres build/compare
     * the geography value keeps `ddl-auto: validate` out of this decision
     * entirely.
     */
    @Query(value = """
            SELECT w.worker_id AS workerId,
                   ST_Distance(wl.geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography) / 1000.0 AS distanceKm
            FROM workers w
            JOIN locations wl ON wl.location_id = w.location_id
            WHERE w.status = 'active'
              AND ST_DWithin(wl.geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radiusMeters)
            ORDER BY distanceKm
            """, nativeQuery = true)
    List<NearbyWorkerProjection> findWorkersWithinRadius(
            @Param("lat") double lat, @Param("lon") double lon, @Param("radiusMeters") double radiusMeters);

    /**
     * Wraps {@code fn_score_candidate(p_booking_id, p_worker_id)}
     * (05_functions_procedures_v3.sql) — the same weighted 6-factor scoring
     * function {@code sp_allocate_worker} itself calls for every candidate
     * before picking the best one. Columns are explicitly aliased to their
     * camelCase projection getter names, same convention as
     * {@link #findWorkersWithinRadius} — no scoring math is reimplemented
     * in Java, this is a thin pass-through.
     */
    @Query(value = """
            SELECT skill_score AS skillScore,
                   rating_score AS ratingScore,
                   distance_score AS distanceScore,
                   workload_score AS workloadScore,
                   price_score AS priceScore,
                   experience_score AS experienceScore,
                   total_score AS totalScore
            FROM fn_score_candidate(:bookingId, :workerId)
            """, nativeQuery = true)
    AllocationScoreProjection scoreCandidate(@Param("bookingId") Long bookingId, @Param("workerId") Long workerId);
}
