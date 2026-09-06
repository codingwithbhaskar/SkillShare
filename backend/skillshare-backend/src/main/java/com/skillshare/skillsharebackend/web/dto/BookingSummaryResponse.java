package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.enums.BookingStatus;
import com.skillshare.skillsharebackend.domain.enums.BookingUrgency;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Response shape for {@code GET /api/workers/{id}/bookings} - lighter
 *  than {@link BookingResponse} (no locationId/notes), built inside
 *  {@code WorkerDashboardService}'s transaction for the same
 *  open-in-view=false reason documented on {@link BookingResponse}. */
public record BookingSummaryResponse(
        Long bookingId,
        Long customerId,
        Long serviceId,
        BookingStatus status,
        BookingUrgency urgency,
        OffsetDateTime scheduledStart,
        OffsetDateTime scheduledEnd,
        BigDecimal totalAmount) {

    public static BookingSummaryResponse from(Booking booking) {
        return new BookingSummaryResponse(
                booking.getBookingId(),
                booking.getCustomer().getUserId(),
                booking.getService().getServiceId(),
                booking.getStatus(),
                booking.getUrgency(),
                booking.getScheduledStart(),
                booking.getScheduledEnd(),
                booking.getTotalAmount());
    }
}
