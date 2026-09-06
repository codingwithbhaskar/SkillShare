package com.skillshare.skillsharebackend.stats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes the {@code mv_worker_stats} staleness gap found during Phase 8's
 * full retest (phase-8-frontend-in-progress.md, "Full retest round 2"):
 * the materialized view backing both the worker dashboard's stat cards
 * AND {@code fn_score_candidate}'s {@code avg_rating}/{@code
 * active_bookings} scoring factors was previously refreshed exactly twice,
 * both inside {@code V5__seed.sql}, and never again.
 *
 * <p>Two call sites keep it fresh, per the chosen "both" strategy:
 * <ul>
 *   <li>{@link com.skillshare.skillsharebackend.stats.WorkerStatsRefreshJob}
 *       - a periodic {@code @Scheduled} safety net, same pattern as
 *       {@code NotificationDispatchJob}.</li>
 *   <li>A synchronous call right after any write that changes a worker's
 *       {@code active_bookings}/{@code completed_bookings}/{@code
 *       avg_rating} — see {@code BookingService#cancelBooking}/{@code
 *       #completeBooking}, {@code AllocationService#allocate}, and {@code
 *       ReviewService#submitReview} — so the very next allocation scored
 *       right after a completion/cancellation/review sees current data,
 *       not stale seed-time numbers.</li>
 * </ul>
 *
 * <p>{@code REFRESH MATERIALIZED VIEW CONCURRENTLY} (not the plain form)
 * is used specifically so a refresh never blocks concurrent reads of the
 * view — {@code fn_score_candidate} keeps working against the
 * previous snapshot for the brief moment a refresh is in flight, rather
 * than being locked out. This requires a unique index on the view, which
 * {@code V1__schema.sql} already created
 * ({@code uq_mv_worker_stats_worker_id}) — no migration needed.
 *
 * <p>No explicit transaction demarcation beyond Spring's default
 * (REQUIRED): called synchronously from inside an already-{@code
 * @Transactional} write method, this joins that same transaction, so the
 * refresh sees that method's own not-yet-committed row change too
 * (Postgres lets a transaction see its own writes) — the dashboard/
 * scoring numbers are correct even before the outer transaction commits.
 * Called from the scheduled job (no ambient transaction), it simply opens
 * its own short one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkerStatsRefreshService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void refresh() {
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_worker_stats");
        log.debug("WorkerStatsRefreshService: mv_worker_stats refreshed");
    }
}
