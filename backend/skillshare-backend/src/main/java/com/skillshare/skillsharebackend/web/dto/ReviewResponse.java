package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.Review;

import java.time.OffsetDateTime;

/** Response shape for {@code POST /api/bookings/{id}/review} and
 *  {@code GET /api/workers/{id}/reviews}. */
public record ReviewResponse(
        Long reviewId,
        Long bookingId,
        Long customerId,
        Long workerId,
        Short rating,
        String comment,
        OffsetDateTime createdAt) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getReviewId(),
                review.getBooking().getBookingId(),
                review.getCustomer().getUserId(),
                review.getWorker().getWorkerId(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt());
    }
}
