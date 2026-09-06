package com.skillshare.skillsharebackend.allocation;

import com.skillshare.skillsharebackend.repository.AllocationScoreProjection;
import com.skillshare.skillsharebackend.repository.BookingRepository;
import com.skillshare.skillsharebackend.repository.WorkerRepository;
import com.skillshare.skillsharebackend.web.dto.CandidateScoreResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AllocationScoringService} — the Phase 4 wrapper
 * around fn_score_candidate. WorkerRepository is mocked: this checks the
 * service's own mapping/sorting/validation logic, not the scoring math
 * itself (that's PL/pgSQL, already exercised for real in
 * schema-v3-triggers-functions.md's live allocation run).
 */
@ExtendWith(MockitoExtension.class)
class AllocationScoringServiceTest {

    @Mock
    private WorkerRepository workerRepository;
    @Mock
    private BookingRepository bookingRepository;

    private AllocationScoringService service;

    @BeforeEach
    void setUp() {
        service = new AllocationScoringService(workerRepository, bookingRepository);
    }

    @Test
    void scoreCandidate_mapsProjectionToResponse() {
        when(bookingRepository.existsById(4L)).thenReturn(true);
        when(workerRepository.existsById(1L)).thenReturn(true);
        AllocationScoreProjection projection = scoreOf("0.5", "0.6", "0.8", "1.0", "0.3", "0.7", "0.6446");
        when(workerRepository.scoreCandidate(4L, 1L)).thenReturn(projection);

        CandidateScoreResponse result = service.scoreCandidate(4L, 1L);

        assertEquals(new CandidateScoreResponse(1L,
                new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.8"),
                new BigDecimal("1.0"), new BigDecimal("0.3"), new BigDecimal("0.7"),
                new BigDecimal("0.6446")), result);
    }

    @Test
    void scoreCandidate_throwsBookingNotFoundException_forUnknownBooking() {
        when(bookingRepository.existsById(999L)).thenReturn(false);

        assertThrows(BookingNotFoundException.class, () -> service.scoreCandidate(999L, 1L));
    }

    @Test
    void scoreCandidate_throwsWorkerNotFoundException_forUnknownWorker() {
        when(bookingRepository.existsById(4L)).thenReturn(true);
        when(workerRepository.existsById(999L)).thenReturn(false);

        assertThrows(WorkerNotFoundException.class, () -> service.scoreCandidate(4L, 999L));
    }

    @Test
    void scoreAllCandidates_sortsBestScoreFirst() {
        when(bookingRepository.existsById(4L)).thenReturn(true);
        when(workerRepository.findCandidateWorkerIds(4L)).thenReturn(List.of(1L, 2L));
        AllocationScoreProjection lowerScore = scoreOf("1.0", "1.0", "1.0", "1.0", "1.0", "1.0", "0.4000");
        AllocationScoreProjection higherScore = scoreOf("1.0", "1.0", "1.0", "1.0", "1.0", "1.0", "0.8446");
        when(workerRepository.scoreCandidate(4L, 1L)).thenReturn(lowerScore);
        when(workerRepository.scoreCandidate(4L, 2L)).thenReturn(higherScore);

        List<CandidateScoreResponse> result = service.scoreAllCandidates(4L);

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).workerId()); // higher total score sorts first
        assertEquals(1L, result.get(1).workerId());
    }

    @Test
    void scoreAllCandidates_returnsEmptyList_whenNoCandidatesEligible() {
        when(bookingRepository.existsById(4L)).thenReturn(true);
        when(workerRepository.findCandidateWorkerIds(4L)).thenReturn(List.of());

        List<CandidateScoreResponse> result = service.scoreAllCandidates(4L);

        assertTrue(result.isEmpty());
    }

    @Test
    void scoreAllCandidates_throwsBookingNotFoundException_forUnknownBooking() {
        when(bookingRepository.existsById(999L)).thenReturn(false);

        assertThrows(BookingNotFoundException.class, () -> service.scoreAllCandidates(999L));
    }

    private AllocationScoreProjection scoreOf(String skill, String rating, String distance,
                                               String workload, String price, String experience, String total) {
        AllocationScoreProjection p = mock(AllocationScoreProjection.class);
        when(p.getSkillScore()).thenReturn(new BigDecimal(skill));
        when(p.getRatingScore()).thenReturn(new BigDecimal(rating));
        when(p.getDistanceScore()).thenReturn(new BigDecimal(distance));
        when(p.getWorkloadScore()).thenReturn(new BigDecimal(workload));
        when(p.getPriceScore()).thenReturn(new BigDecimal(price));
        when(p.getExperienceScore()).thenReturn(new BigDecimal(experience));
        when(p.getTotalScore()).thenReturn(new BigDecimal(total));
        return p;
    }
}
