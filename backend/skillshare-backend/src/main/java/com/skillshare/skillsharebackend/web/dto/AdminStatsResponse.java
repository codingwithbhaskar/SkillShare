package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.enums.BookingStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Response shape for {@code GET /api/admin/stats} - the admin
 *  dashboard's stat cards + recent-activity table. {@code totalRevenue}
 *  is the sum of every completed payment ever recorded (all-time, not
 *  date-scoped - see {@link AdminReportResponse} for the date-range
 *  version of revenue reporting). */
public record AdminStatsResponse(
        long totalUsers,
        long totalCustomers,
        long totalWorkers,
        long totalAdmins,
        long activeUsers,
        long suspendedUsers,
        long totalServices,
        long totalSkills,
        long totalBookings,
        Map<BookingStatus, Long> bookingsByStatus,
        BigDecimal totalRevenue,
        List<AdminBookingResponse> recentBookings) {}
