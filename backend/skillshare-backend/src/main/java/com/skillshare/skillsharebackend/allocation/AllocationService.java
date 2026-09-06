package com.skillshare.skillsharebackend.allocation;

import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.enums.BookingStatus;
import com.skillshare.skillsharebackend.repository.BookingRepository;
import com.skillshare.skillsharebackend.security.AuthenticatedUser;
import com.skillshare.skillsharebackend.security.ForbiddenException;
import com.skillshare.skillsharebackend.stats.WorkerStatsRefreshService;
import com.skillshare.skillsharebackend.web.dto.AllocationResultResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Phase 4 — the intelligent allocator, wrapping {@code sp_allocate_worker}
 * (05_functions_procedures_v3.sql) exactly as-is: row-locks the booking,
 * scores every hard-filtered candidate via fn_score_candidate, logs all of
 * them to {@code allocation_log}, then walks candidates best-score-first
 * inside its own {@code BEGIN...EXCEPTION WHEN exclusion_violation} block,
 * retrying the next candidate whenever it loses a race for that worker's
 * time slot. That exclusion-violation handling is entirely owned by the
 * stored procedure and already proven correct against two genuinely
 * concurrent psql sessions (see schema-v3-triggers-functions.md's real
 * concurrency test) — this class does not, and deliberately does not try
 * to, reimplement or retry around it.
 *
 * <p><b>What this class DOES own</b>, because the procedure doesn't:
 * <ul>
 *   <li><b>Rejecting a call for a booking that isn't {@code pending},
 *       race-safely.</b> See {@link BookingNotAllocatableException}'s
 *       javadoc for why calling sp_allocate_worker again on an already-
 *       confirmed booking is unsafe (it will happily reassign a possibly-
 *       different worker). The pre-check reads the booking via
 *       {@link com.skillshare.skillsharebackend.repository.
 *       BookingRepository#findByIdForUpdate}, a {@code SELECT ... FOR
 *       UPDATE} — not a plain read — so that two genuinely concurrent
 *       {@code allocate} calls for the *same* booking ID can't both pass
 *       the pending check before either one commits. The second call
 *       blocks on that lock until the first's transaction finishes, then
 *       correctly sees the now-{@code confirmed} status and throws,
 *       instead of silently reprocessing and possibly reassigning a
 *       different worker.</li>
 *   <li><b>Reporting the actual outcome.</b> {@code CALL
 *       sp_allocate_worker(?)} gives its JDBC caller no return value and
 *       throws no exception either way — "no eligible worker could be
 *       confirmed" is only a {@code RAISE NOTICE} inside the procedure
 *       body, which plain JDBC never surfaces as a Java-visible signal.
 *       The booking's own row is re-queried, in the same transaction,
 *       immediately after the CALL to see what actually happened.</li>
 * </ul>
 *
 * <p><b>No Spring Retry / {@code @Retryable} here</b>, despite earlier
 * roadmap notes naming it: the one exclusion-violation race this procedure
 * can hit is already fully absorbed inside its own per-candidate loop, so
 * there's nothing left for a Java-side retry to meaningfully do in normal
 * operation. Adding the {@code spring-retry} + {@code spring-boot-starter-
 * aop} dependencies and the AOP proxy wiring {@code @Retryable} needs —
 * for a race condition the DB layer already owns, with no way to compile/
 * exercise that proxy interaction in this development sandbox — would be
 * complexity without a real payoff. If a future phase adds a distinct DB
 * call here that CAN legitimately throw a transient error (e.g. a real
 * multi-row deadlock), that's the moment to revisit this decision, not
 * before.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AllocationService {

    private final BookingRepository bookingRepository;
    private final JdbcTemplate jdbcTemplate;
    private final WorkerStatsRefreshService workerStatsRefreshService;

    @Transactional
    public AllocationResultResponse allocate(Long bookingId) {
        // findByIdForUpdate, not findById: see this class's javadoc on the
        // race this pessimistic lock closes.
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (booking.getStatus() != BookingStatus.pending) {
            throw new BookingNotAllocatableException(bookingId, booking.getStatus());
        }

        return doAllocate(bookingId);
    }

    /** Ownership-checked overload {@code AllocationController} calls -
     *  only the booking's own customer (or an admin) may trigger
     *  allocation for it, closing the same gap
     *  {@code BookingService}/{@code ReviewService}'s overloads close. */
    @Transactional
    public AllocationResultResponse allocate(Long bookingId, AuthenticatedUser caller) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        boolean isOwner = booking.getCustomer().getUserId().equals(caller.userId());
        if (!isOwner && !caller.isAdmin()) {
            throw new ForbiddenException("You do not have access to booking " + bookingId);
        }

        if (booking.getStatus() != BookingStatus.pending) {
            throw new BookingNotAllocatableException(bookingId, booking.getStatus());
        }

        return doAllocate(bookingId);
    }

    private AllocationResultResponse doAllocate(Long bookingId) {
        jdbcTemplate.update("CALL sp_allocate_worker(?)", bookingId);

        // Re-query the booking's own row directly via JDBC rather than
        // through the JPA persistence context: the CALL above ran on the
        // same connection/transaction but outside Hibernate's session, so
        // the `booking` entity already loaded above is now stale in
        // Hibernate's first-level cache and would silently return its old
        // (pending, no worker) values if re-fetched via the repository
        // instead of a fresh query.
        //
        // `status::text` is a deliberate cast, not decoration: this is a
        // native enum column read through plain JDBC rather than through
        // Hibernate's own NAMED_ENUM binding, and pgJDBC's default
        // getObject() handling of unregistered custom enum types is not
        // guaranteed to hand back a plain String across driver versions —
        // exactly the kind of silent type surprise that caused the Phase 3
        // enum bug. Casting to text in the SQL itself removes all doubt.
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status::text AS status, worker_id FROM bookings WHERE booking_id = ?", bookingId);

        BookingStatus resultStatus = BookingStatus.valueOf((String) row.get("status"));
        Long assignedWorkerId = row.get("worker_id") == null
                ? null
                : ((Number) row.get("worker_id")).longValue();
        boolean allocated = assignedWorkerId != null;

        if (allocated) {
            log.info("AllocationService: booking {} -> worker {} (status {})",
                    bookingId, assignedWorkerId, resultStatus);
            // The just-assigned worker's active_bookings just went up -
            // see WorkerStatsRefreshService's javadoc for why this keeps
            // the very next scoring call (for a different booking) fair.
            workerStatsRefreshService.refresh();
        } else {
            log.info("AllocationService: no eligible worker could be confirmed for booking {} "
                    + "(status remains {})", bookingId, resultStatus);
        }

        return new AllocationResultResponse(bookingId, allocated, resultStatus, assignedWorkerId);
    }
}
