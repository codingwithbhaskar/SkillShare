package com.skillshare.skillsharebackend.allocation;

import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.repository.BookingRepository;
import com.skillshare.skillsharebackend.repository.NearbyWorkerProjection;
import com.skillshare.skillsharebackend.repository.WorkerRepository;
import com.skillshare.skillsharebackend.web.dto.CandidateWorkerResponse;
import com.skillshare.skillsharebackend.web.dto.NearbyWorkerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Phase 3 — spatial candidate retrieval. Two independent capabilities that
 * happen to share the same underlying PostGIS machinery
 * ({@code geography(Point,4326)}, {@code ST_DWithin}/{@code ST_Distance}):
 *
 * <ul>
 *   <li>{@link #findCandidatesForBooking} wraps fn_find_candidates — the
 *       same hard-filter candidate set sp_allocate_worker scores and picks
 *       from, exposed here read-only for previewing/debugging allocation
 *       without actually running the allocation.</li>
 *   <li>{@link #findNearbyWorkers} is a general "workers near this point"
 *       radius search, independent of any booking - the Phase 3 roadmap's
 *       standalone radius-search endpoint.</li>
 * </ul>
 *
 * Both are thin wrappers over native SQL (see {@code WorkerRepository}) -
 * no allocation/distance math is reimplemented in Java, consistent with
 * this project's architectural principle that allocation-critical logic
 * lives in PL/pgSQL.
 */
@Service
@RequiredArgsConstructor
public class SpatialSearchService {

    private final WorkerRepository workerRepository;
    private final BookingRepository bookingRepository;

    public List<NearbyWorkerResponse> findNearbyWorkers(BigDecimal latitude, BigDecimal longitude, double radiusKm) {
        double radiusMeters = radiusKm * 1000.0;
        return workerRepository
                .findWorkersWithinRadius(latitude.doubleValue(), longitude.doubleValue(), radiusMeters)
                .stream()
                .map(this::toNearbyWorkerResponse)
                .toList();
    }

    public List<CandidateWorkerResponse> findCandidatesForBooking(Long bookingId) {
        if (!bookingRepository.existsById(bookingId)) {
            throw new BookingNotFoundException(bookingId);
        }

        List<Long> candidateWorkerIds = workerRepository.findCandidateWorkerIds(bookingId);
        if (candidateWorkerIds.isEmpty()) {
            return List.of();
        }

        // findAllById's result order isn't guaranteed to match the input
        // list - fine here, fn_find_candidates itself has no ORDER BY
        // either, so "the set of eligible workers" is what matters, not a
        // particular ordering.
        return workerRepository.findAllById(candidateWorkerIds).stream()
                .map(this::toCandidateWorkerResponse)
                .toList();
    }

    private NearbyWorkerResponse toNearbyWorkerResponse(NearbyWorkerProjection projection) {
        return new NearbyWorkerResponse(projection.getWorkerId(), projection.getDistanceKm());
    }

    private CandidateWorkerResponse toCandidateWorkerResponse(Worker worker) {
        return new CandidateWorkerResponse(
                worker.getWorkerId(),
                worker.getExperienceYears(),
                worker.getBaseHourlyRate(),
                worker.getStatus());
    }
}
