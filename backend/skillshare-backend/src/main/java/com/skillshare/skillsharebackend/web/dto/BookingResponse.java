package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.enums.BookingStatus;
import com.skillshare.skillsharebackend.domain.enums.BookingUrgency;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Response shape for every booking-lifecycle endpoint in
 *  {@code BookingController} (create/cancel/start/complete). Built inside
 *  the owning {@code @Transactional} service method, never in the
 *  controller - {@code spring.jpa.open-in-view} is {@code false}
 *  (application.yml), so a lazy association like {@code booking.customer}
 *  would throw {@code LazyInitializationException} if touched after the
 *  transaction/session has already closed. */
public record BookingResponse(
        Long bookingId,
        Long customerId,
        Long workerId,
        Long serviceId,
        Long locationId,
        BookingStatus status,
        BookingUrgency urgency,
        OffsetDateTime scheduledStart,
        OffsetDateTime scheduledEnd,
        BigDecimal totalAmount,
        String notes,
        // Denormalized from the booking's Location row - added for Phase 8's
        // map view (location-map-feature-design.md) so the frontend doesn't
        // need a second round-trip (no GET /api/locations/{id} endpoint
        // exists, and didn't need to before a browser client existed).
        String addressLine,
        String city,
        BigDecimal latitude,
        BigDecimal longitude,
        // Closes the "dangling review form" gap found in Phase 8's full
        // retest (phase-8-frontend-in-progress.md): without this, the
        // frontend had no way to know a completed booking already has a
        // review, so a fresh page load kept showing the review form again
        // until Submit was clicked and the backend's duplicate-review
        // guard caught it. Always false for a non-completed booking
        // (a review can't exist yet) - see BookingService's two
        // BookingResponse.from(...) call sites for how this is computed.
        boolean reviewed,
        // The assigned worker's own name/phone/bio/rate/rating - null
        // until a worker is allocated. Added so the frontend can show WHO
        // is actually coming to do the job, not just a bare workerId.
        AssignedWorkerResponse worker) {

    /** For call sites where "reviewed" is always false by construction -
     *  a booking that's just been created, cancelled, or started can
     *  never already have a review (reviews require {@code completed}
     *  status first), and a booking that's just transitioned TO
     *  {@code completed} can't have one yet either, since submitting one
     *  is a separate, later call. Only {@code BookingService#getBooking}
     *  and {@code #getBookingsForCustomer} need the real, queried value -
     *  see the other factory below. */
    public static BookingResponse from(Booking booking) {
        return from(booking, false);
    }

    /** No {@code mv_worker_stats} lookup - see {@link
     *  AssignedWorkerResponse#from(com.skillshare.skillsharebackend.domain.Worker)}'s
     *  javadoc for why that's fine at this call site (every write-path
     *  caller here has the frontend re-fetch via {@code GET
     *  /api/bookings/{id}} right after anyway, which goes through the
     *  full factory below instead). */
    public static BookingResponse from(Booking booking, boolean reviewed) {
        return from(booking, reviewed,
                booking.getWorker() != null ? AssignedWorkerResponse.from(booking.getWorker()) : null);
    }

    /** Full form, used by {@code BookingService.toResponseWithReviewFlag}
     *  once it's looked up the assigned worker's {@code mv_worker_stats}
     *  row (if any) - the only call site that needs an already-built
     *  {@code worker} passed in rather than derived straight from the
     *  booking. */
    public static BookingResponse from(Booking booking, boolean reviewed, AssignedWorkerResponse worker) {
        return new BookingResponse(
                booking.getBookingId(),
                booking.getCustomer().getUserId(),
                booking.getWorker() != null ? booking.getWorker().getWorkerId() : null,
                booking.getService().getServiceId(),
                booking.getLocation().getLocationId(),
                booking.getStatus(),
                booking.getUrgency(),
                booking.getScheduledStart(),
                booking.getScheduledEnd(),
                booking.getTotalAmount(),
                booking.getNotes(),
                booking.getLocation().getAddressLine(),
                booking.getLocation().getCity(),
                booking.getLocation().getLatitude(),
                booking.getLocation().getLongitude(),
                reviewed,
                worker);
    }
}
