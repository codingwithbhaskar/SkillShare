package com.skillshare.skillsharebackend.web.dto;

/** Request body for {@code POST /api/bookings/{id}/review}. */
public record SubmitReviewRequest(Integer rating, String comment) {
}
