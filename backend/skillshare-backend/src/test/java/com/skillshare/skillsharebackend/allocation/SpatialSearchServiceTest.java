package com.skillshare.skillsharebackend.allocation;

import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import com.skillshare.skillsharebackend.repository.BookingRepository;
import com.skillshare.skillsharebackend.repository.NearbyWorkerProjection;
import com.skillshare.skillsharebackend.repository.WorkerRepository;
import com.skillshare.skillsharebackend.web.dto.CandidateWorkerResponse;
import com.skillshare.skillsharebackend.web.dto.NearbyWorkerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SpatialSearchService} - the Phase 3 service that
 * wraps fn_find_candidates and the general radius search, both via native
 * queries on {@link WorkerRepository}. Both repositories are mocked: this
 * checks the service's own delegation/mapping/validation logic, not
 * PostGIS itself (the ST_DWithin/ST_Distance math is exercised for real by
 * fn_find_candidates/fn_score_candidate's own live-database verification,
 * recorded in schema-v3-triggers-functions.md).
 */
@ExtendWith(MockitoExtension.class)
class SpatialSearchServiceTest {

    @Mock
    private WorkerRepository workerRepository;
    @Mock
    private BookingRepository bookingRepository;

    private SpatialSearchService service;

    @BeforeEach
    void setUp() {
        service = new SpatialSearchService(workerRepository, bookingRepository);
    }

    @Test
    void findNearbyWorkers_convertsRadiusKmToMeters_andMapsProjectionsToResponses() {
        BigDecimal lat = new BigDecimal("19.997454");
        BigDecimal lon = new BigDecimal("73.789803");

        NearbyWorkerProjection near = mock(NearbyWorkerProjection.class);
        when(near.getWorkerId()).thenReturn(1L);
        when(near.getDistanceKm()).thenReturn(1.08);

        NearbyWorkerProjection far = mock(NearbyWorkerProjection.class);
        when(far.getWorkerId()).thenReturn(2L);
        when(far.getDistanceKm()).thenReturn(9.5);

        when(workerRepository.findWorkersWithinRadius(lat.doubleValue(), lon.doubleValue(), 15_000.0))
                .thenReturn(List.of(near, far));

        List<NearbyWorkerResponse> result = service.findNearbyWorkers(lat, lon, 15.0);

        assertEquals(2, result.size());
        assertEquals(new NearbyWorkerResponse(1L, 1.08), result.get(0));
        assertEquals(new NearbyWorkerResponse(2L, 9.5), result.get(1));

        // radiusKm -> meters conversion is the one piece of arithmetic this
        // service does itself; pin it down explicitly with a captor rather
        // than relying on the exact-match stub above alone.
        ArgumentCaptor<Double> radiusCaptor = ArgumentCaptor.forClass(Double.class);
        verify(workerRepository).findWorkersWithinRadius(anyDouble(), anyDouble(), radiusCaptor.capture());
        assertEquals(15_000.0, radiusCaptor.getValue(), 0.0001);
    }

    @Test
    void findNearbyWorkers_returnsEmptyList_whenNoWorkersInRange() {
        when(workerRepository.findWorkersWithinRadius(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());

        List<NearbyWorkerResponse> result = service.findNearbyWorkers(new BigDecimal("0"), new BigDecimal("0"), 5.0);

        assertTrue(result.isEmpty());
    }

    @Test
    void findCandidatesForBooking_mapsEligibleWorkers() {
        Long bookingId = 4L;
        when(bookingRepository.existsById(bookingId)).thenReturn(true);
        when(workerRepository.findCandidateWorkerIds(bookingId)).thenReturn(List.of(1L, 2L));

        Worker ravi = Worker.builder()
                .workerId(1L).experienceYears((short) 8)
                .baseHourlyRate(new BigDecimal("450.00"))
                .status(AccountStatus.active)
                .build();
        Worker sunil = Worker.builder()
                .workerId(2L).experienceYears((short) 3)
                .baseHourlyRate(new BigDecimal("300.00"))
                .status(AccountStatus.active)
                .build();
        when(workerRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(ravi, sunil));

        List<CandidateWorkerResponse> result = service.findCandidatesForBooking(bookingId);

        assertEquals(2, result.size());
        assertTrue(result.contains(new CandidateWorkerResponse(1L, (short) 8, new BigDecimal("450.00"), AccountStatus.active)));
        assertTrue(result.contains(new CandidateWorkerResponse(2L, (short) 3, new BigDecimal("300.00"), AccountStatus.active)));
    }

    @Test
    void findCandidatesForBooking_returnsEmptyList_whenNoCandidatesEligible() {
        Long bookingId = 5L;
        when(bookingRepository.existsById(bookingId)).thenReturn(true);
        when(workerRepository.findCandidateWorkerIds(bookingId)).thenReturn(Collections.emptyList());

        List<CandidateWorkerResponse> result = service.findCandidatesForBooking(bookingId);

        assertTrue(result.isEmpty());
    }

    @Test
    void findCandidatesForBooking_throwsBookingNotFoundException_forUnknownBooking() {
        Long unknownBookingId = 999L;
        when(bookingRepository.existsById(unknownBookingId)).thenReturn(false);

        assertThrows(BookingNotFoundException.class, () -> service.findCandidatesForBooking(unknownBookingId));
    }
}
