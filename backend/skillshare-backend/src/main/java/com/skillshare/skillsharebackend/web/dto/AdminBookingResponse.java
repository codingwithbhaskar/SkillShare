package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.enums.BookingStatus;
import com.skillshare.skillsharebackend.domain.enums.BookingUrgency;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Response shape for {@code GET /api/admin/bookings} and the dashboard's
 *  "recent bookings" list - denormalizes customer/worker/service names
 *  directly onto the row (unlike {@link BookingSummaryResponse}, which
 *  only carries ids) since an admin table has no other context to
 *  resolve them from and no per-row detail fetch to fall back on. */
public record AdminBookingResponse(
        Long bookingId,
        Long customerId,
        String customerName,
        Long workerId,
        String workerName,
        String serviceName,
        BookingStatus status,
        BookingUrgency urgency,
        OffsetDateTime scheduledStart,
        OffsetDateTime scheduledEnd,
        BigDecimal totalAmount) {

    public static AdminBookingResponse from(Booking booking) {
        return new AdminBookingResponse(
                booking.getBookingId(),
                booking.getCustomer().getUserId(),
                booking.getCustomer().getFullName(),
                booking.getWorker() != null ? booking.getWorker().getWorkerId() : null,
                booking.getWorker() != null ? booking.getWorker().getUser().getFullName() : null,
                booking.getService().getServiceName(),
                booking.getStatus(),
                booking.getUrgency(),
                booking.getScheduledStart(),
                booking.getScheduledEnd(),
                booking.getTotalAmount());
    }
}
