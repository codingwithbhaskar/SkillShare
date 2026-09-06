package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.enums.BookingUrgency;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Request body for {@code POST /api/bookings}. {@code urgency} is
 *  optional and defaults to {@code normal} when omitted - see
 *  {@link com.skillshare.skillsharebackend.booking.BookingService#
 *  createBooking}. The inline address/lat/lon fields always create a
 *  fresh {@code Location} row - see that method's javadoc for why
 *  bookings never reuse a worker's or customer's existing location. */
public record CreateBookingRequest(
        Long customerId,
        Long serviceId,
        List<Long> skillIds,
        BookingUrgency urgency,
        OffsetDateTime scheduledStart,
        OffsetDateTime scheduledEnd,
        String notes,
        String addressLine,
        String landmark,
        String city,
        String state,
        String pincode,
        BigDecimal latitude,
        BigDecimal longitude) {
}
