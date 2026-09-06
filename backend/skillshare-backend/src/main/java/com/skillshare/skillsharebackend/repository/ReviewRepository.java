package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
