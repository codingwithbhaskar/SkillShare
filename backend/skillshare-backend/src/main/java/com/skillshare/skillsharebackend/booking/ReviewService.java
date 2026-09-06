package com.skillshare.skillsharebackend.booking;

import com.skillshare.skillsharebackend.allocation.BookingNotFoundException;
import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.Review;
import com.skillshare.skillsharebackend.domain.enums.BookingStatus;
import com.skillshare.skillsharebackend.repository.BookingRepository;
import com.skillshare.skillsharebackend.repository.ReviewRepository;
import com.skillshare.skillsharebackend.security.AuthenticatedUser;
import com.skillshare.skillsharebackend.security.ForbiddenException;
import com.skillshare.skillsharebackend.stats.WorkerStatsRefreshService;
import com.skillshare.skillsharebackend.web.dto.ReviewResponse;
import com.skillshare.skillsharebackend.web.dto.SubmitReviewRequest;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 5 - review submission. {@code trg_notify_review_received}
 * (04_triggers_v3.sql) fires the worker notification automatically on
 * INSERT; this class does nothing extra for that - same "let the trigger
 * own it" principle {@link BookingService} follows for status-change
 * notifications.
 *
 * <p>Uses {@code findByIdForUpdate} (not a plain read) for the same
 * reason {@link BookingService}'s transitions do: it closes the race
 * where two concurrent review submissions for the same booking both pass
 * the "no review yet" check before either commits. {@code
 * reviews.booking_id UNIQUE} is still the ultimate backstop at the DB
 * level either way.
 *
 * <p>{@link #submitReview(Long, SubmitReviewRequest, AuthenticatedUser)}
 * is the ownership-checked overload {@code BookingController} calls -
 * only the booking's own customer (or an admin) may leave a review for
 * it, closing the same client-supplied-id-trust gap
 * {@link BookingService}'s overloads close.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final EntityManager entityManager;
    private final WorkerStatsRefreshService workerStatsRefreshService;

    @Transactional
    public ReviewResponse submitReview(Long bookingId, SubmitReviewRequest request) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        return doSubmitReview(booking, request);
    }

    @Transactional
    public ReviewResponse submitReview(Long bookingId, SubmitReviewRequest request, AuthenticatedUser caller) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        boolean isOwner = booking.getCustomer().getUserId().equals(caller.userId());
        if (!isOwner && !caller.isAdmin()) {
            throw new ForbiddenException("You do not have access to booking " + bookingId);
        }
        return doSubmitReview(booking, request);
    }

    private ReviewResponse doSubmitReview(Booking booking, SubmitReviewRequest request) {
        Long bookingId = booking.getBookingId();
        if (booking.getStatus() != BookingStatus.completed) {
            throw new ReviewNotAllowedException(bookingId,
                    "booking status is " + booking.getStatus() + ", not completed");
        }
        if (booking.getWorker() == null) {
            throw new ReviewNotAllowedException(bookingId, "booking has no assigned worker");
        }
        if (reviewRepository.findByBooking_BookingId(bookingId).isPresent()) {
            throw new ReviewNotAllowedException(bookingId, "a review already exists for this booking");
        }
        if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw new BookingValidationException("rating must be between 1 and 5");
        }

        Review review = reviewRepository.save(Review.builder()
                .booking(booking)
                .customer(booking.getCustomer())
                .worker(booking.getWorker())
                .rating(request.rating().shortValue())
                .comment(request.comment())
                .build());

        // created_at is DB-generated (insertable=false, DEFAULT now()) -
        // Hibernate never automatically re-reads a generated column's real
        // value back into the entity after INSERT, so without this refresh
        // the object below would still be holding the null it had before
        // the row existed. entityManager.refresh() re-issues a SELECT and
        // overwrites the entity's in-memory state from the DB, which a
        // plain repository re-fetch would NOT do here - the first-level
        // (session) cache would just hand back this same stale instance
        // instead of hitting the database again. Same underlying pitfall
        // AllocationService's javadoc documents for its own post-CALL
        // re-query, just solved with JPA's refresh() here instead of raw
        // JDBC since there's no enum-casting risk on a plain timestamp
        // column.
        entityManager.refresh(review);

        // avg_rating/review_count for this worker just changed - see
        // WorkerStatsRefreshService's javadoc.
        workerStatsRefreshService.refresh();

        log.info("ReviewService: booking {} reviewed ({} stars)", bookingId, request.rating());

        return ReviewResponse.from(review);
    }
}
