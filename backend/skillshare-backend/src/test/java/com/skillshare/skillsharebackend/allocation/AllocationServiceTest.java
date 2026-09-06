package com.skillshare.skillsharebackend.allocation;

import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.enums.BookingStatus;
import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.repository.BookingRepository;
import com.skillshare.skillsharebackend.security.AuthenticatedUser;
import com.skillshare.skillsharebackend.security.ForbiddenException;
import com.skillshare.skillsharebackend.stats.WorkerStatsRefreshService;
import com.skillshare.skillsharebackend.web.dto.AllocationResultResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AllocationService}. JdbcTemplate is mocked — the
 * real {@code CALL sp_allocate_worker(?)} round-trip (row lock, scoring,
 * exclusion-violation retry-next-candidate loop, notification firing) is
 * already proven for real against two genuinely concurrent psql sessions
 * in schema-v3-triggers-functions.md. These tests pin down
 * AllocationService's own pre-check (booking must exist and be pending)
 * and outcome-interpretation logic (re-reading the booking row after the
 * CALL, since the procedure itself signals nothing back to JDBC).
 *
 * <p>Note: these tests stub {@code findByIdForUpdate} rather than
 * {@code findById} — that's the actual method AllocationService calls
 * (the pessimistic-lock version, closing the same-booking concurrent-
 * allocate race). Mockito doesn't exercise real row locking here, of
 * course; that guarantee is inherently a real-database concern, not
 * something a mocked-repository unit test can prove.
 */
@ExtendWith(MockitoExtension.class)
class AllocationServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private WorkerStatsRefreshService workerStatsRefreshService;

    private AllocationService service;

    @BeforeEach
    void setUp() {
        service = new AllocationService(bookingRepository, jdbcTemplate, workerStatsRefreshService);
    }

    @Test
    void allocate_throwsBookingNotFoundException_forUnknownBooking() {
        when(bookingRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThrows(BookingNotFoundException.class, () -> service.allocate(999L));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void allocate_throwsBookingNotAllocatableException_whenBookingNotPending() {
        Booking confirmed = Booking.builder().bookingId(4L).status(BookingStatus.confirmed).build();
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(confirmed));

        assertThrows(BookingNotAllocatableException.class, () -> service.allocate(4L));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void allocate_returnsAllocatedTrue_whenProcedureAssignsAWorker() {
        Booking pending = Booking.builder().bookingId(4L).status(BookingStatus.pending).build();
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(pending));

        Map<String, Object> row = new HashMap<>();
        row.put("status", "confirmed");
        row.put("worker_id", 1L);
        when(jdbcTemplate.queryForMap(anyString(), eq(4L))).thenReturn(row);

        AllocationResultResponse result = service.allocate(4L);

        verify(jdbcTemplate).update(eq("CALL sp_allocate_worker(?)"), eq(4L));
        assertEquals(new AllocationResultResponse(4L, true, BookingStatus.confirmed, 1L), result);
    }

    @Test
    void allocate_returnsAllocatedFalse_whenNoWorkerCouldBeConfirmed() {
        Booking pending = Booking.builder().bookingId(5L).status(BookingStatus.pending).build();
        when(bookingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(pending));

        Map<String, Object> row = new HashMap<>();
        row.put("status", "pending");
        row.put("worker_id", null);
        when(jdbcTemplate.queryForMap(anyString(), eq(5L))).thenReturn(row);

        AllocationResultResponse result = service.allocate(5L);

        assertFalse(result.allocated());
        assertEquals(BookingStatus.pending, result.status());
        assertEquals(5L, result.bookingId());
    }

    @Test
    void allocate_withCaller_throwsForbidden_whenCallerIsNotTheBookingsCustomer() {
        Booking pending = Booking.builder().bookingId(4L).status(BookingStatus.pending)
                .customer(User.builder().userId(1L).build()).build();
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(pending));
        AuthenticatedUser stranger = new AuthenticatedUser(999L, "customer");

        assertThrows(ForbiddenException.class, () -> service.allocate(4L, stranger));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void allocate_withCaller_succeeds_forTheBookingsOwnCustomer() {
        Booking pending = Booking.builder().bookingId(4L).status(BookingStatus.pending)
                .customer(User.builder().userId(1L).build()).build();
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(pending));

        Map<String, Object> row = new HashMap<>();
        row.put("status", "confirmed");
        row.put("worker_id", 1L);
        when(jdbcTemplate.queryForMap(anyString(), eq(4L))).thenReturn(row);

        AuthenticatedUser owner = new AuthenticatedUser(1L, "customer");
        AllocationResultResponse result = service.allocate(4L, owner);

        assertEquals(new AllocationResultResponse(4L, true, BookingStatus.confirmed, 1L), result);
    }
}
