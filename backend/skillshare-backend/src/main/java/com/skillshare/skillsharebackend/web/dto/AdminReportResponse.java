package com.skillshare.skillsharebackend.web.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Response shape for {@code GET /api/admin/reports?from=&to=} - the
 *  "Reports & Analytics" page (the one screen the old v1 project's
 *  summary describes that this app never had a backend equivalent for
 *  until now - see skillshare-v1-old-project-summary.md).
 *
 * <p>{@code totalBookings}/{@code completedBookings}/{@code
 * cancelledBookings} and {@code topWorkers}/{@code topServices} are all
 * scoped to bookings whose {@code scheduledStart} falls in
 * [{@code from}, {@code to}). {@code totalRevenue} is scoped instead by
 * {@code payments.paid_at} in the same window - the actual money that
 * moved during the period, which for a booking scheduled near a
 * boundary can differ slightly from a scheduledStart-based figure. Both
 * are defensible date bases; this mirrors the distinction already drawn
 * between a booking's schedule and its payment lifecycle everywhere else
 * in this codebase (see PaymentService).
 *
 * <p>{@code TopWorker.avgRating} comes from {@code mv_worker_stats} - the
 * same all-time aggregate {@code WorkerStatsResponse} already exposes,
 * not a rating recomputed just for this date range, since reviews don't
 * carry enough volume per worker per window for a windowed average to be
 * meaningful in a course-project-scale dataset. */
public record AdminReportResponse(
        OffsetDateTime from,
        OffsetDateTime to,
        long totalBookings,
        long completedBookings,
        long cancelledBookings,
        BigDecimal totalRevenue,
        List<TopWorker> topWorkers,
        List<TopService> topServices) {

    public record TopWorker(Long workerId, String fullName, long completedBookings, BigDecimal avgRating) {}

    public record TopService(Long serviceId, String serviceName, long bookingCount, BigDecimal revenue) {}
}
