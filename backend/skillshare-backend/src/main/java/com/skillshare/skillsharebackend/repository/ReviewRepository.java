package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Optional<Review> findByBooking_BookingId(Long bookingId);
    List<Review> findByWorker_WorkerId(Long workerId);

    /** Backs {@code BookingResponse.reviewed} - see that record's javadoc.
     *  A plain existence check, cheaper than the full
     *  {@link #findByBooking_BookingId} lookup {@code ReviewService}
     *  already uses for its own duplicate-review guard. */
    boolean existsByBooking_BookingId(Long bookingId);

    /** Batched form of {@link #existsByBooking_BookingId} - one round
     *  trip for a whole list of bookings instead of N, used by
     *  {@code BookingService.getBookingsForCustomer} so the bookings-list
     *  endpoint doesn't pay for one query per row (real, measurable
     *  latency on a cross-region deployment - confirmed live 2026-09-12). */
    @Query("SELECT r.booking.bookingId FROM Review r WHERE r.booking.bookingId IN :bookingIds")
    List<Long> findBookingIdsWithReview(@Param("bookingIds") List<Long> bookingIds);
}
