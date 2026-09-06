package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByCustomer_UserId(Long customerId);

    /**
     * Same lookup as {@link #findById}, but takes a {@code SELECT ... FOR
     * UPDATE} row lock — used by {@code AllocationService.allocate} so its
     * pending-status pre-check is race-safe against a second, genuinely
     * concurrent {@code allocate} call for the same booking ID. Without
     * this, two overlapping calls could both read {@code pending} before
     * either one's transaction commits, and the second would go on to
     * call {@code sp_allocate_worker} a second time on an
     * already-being-allocated booking. This lock is on the exact same
     * {@code bookings} row {@code sp_allocate_worker}'s own {@code PERFORM
     * ... FOR UPDATE} takes (05_functions_procedures_v3.sql) — a
     * transaction that already holds it (via this method) can re-take it
     * inside the CALL without deadlocking itself; a second, different
     * transaction blocks here until the first commits or rolls back, then
     * sees the up-to-date status.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.bookingId = :bookingId")
    Optional<Booking> findByIdForUpdate(@Param("bookingId") Long bookingId);

    List<Booking> findByWorker_WorkerId(Long workerId);

    List<Booking> findByStatus(BookingStatus status);

    /**
     * Any live (non-cancelled) booking for this worker whose window
     * overlaps the given start/end range, excluding the booking being
     * allocated itself. Mirrors the overlap predicate the EXCLUDE USING gist
     * constraint enforces at the DB level (booking_range &&) — used by
     * the traditional allocator to skip workers who are already busy,
     * same idea as fn_find_candidates' NOT EXISTS check in the
     * intelligent allocator, just expressed in JPQL instead of PL/pgSQL.
     */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.worker.workerId = :workerId
              AND b.bookingId <> :excludeBookingId
              AND b.status <> :cancelledStatus
              AND b.scheduledStart < :end
              AND b.scheduledEnd > :start
            """)
    List<Booking> findOverlapping(
            @Param("workerId") Long workerId,
            @Param("excludeBookingId") Long excludeBookingId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("cancelledStatus") BookingStatus cancelledStatus);

    // --- Admin panel additions (AdminService) ---

    long countByStatus(BookingStatus status);

    List<Booking> findTop10ByOrderByCreatedAtDesc();

    List<Booking> findByScheduledStartBetween(OffsetDateTime from, OffsetDateTime to);

    /** Delete-guard for {@code AdminService.deleteService} - a service
     *  with booking history (past or present) can't be removed, since
     *  {@code bookings.service_id} has no ON DELETE action (RESTRICT). */
    boolean existsByService_ServiceId(Long serviceId);
}
