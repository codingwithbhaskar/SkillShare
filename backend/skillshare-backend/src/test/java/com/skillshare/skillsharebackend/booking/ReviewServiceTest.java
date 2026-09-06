package com.skillshare.skillsharebackend.booking;

import com.skillshare.skillsharebackend.allocation.BookingNotFoundException;
import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.Review;
import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.domain.enums.BookingStatus;
import com.skillshare.skillsharebackend.repository.BookingRepository;
import com.skillshare.skillsharebackend.repository.ReviewRepository;
import com.skillshare.skillsharebackend.security.AuthenticatedUser;
import com.skillshare.skillsharebackend.security.ForbiddenException;
import com.skillshare.skillsharebackend.stats.WorkerStatsRefreshService;
import com.skillshare.skillsharebackend.web.dto.ReviewResponse;
import com.skillshare.skillsharebackend.web.dto.SubmitReviewRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Unit tests for {@link ReviewService}, mirroring {@code
 *  BookingServiceTest}'s mocked-repository style. */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private WorkerStatsRefreshService workerStatsRefreshService;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(bookingRepository, reviewRepository, entityManager, workerStatsRefreshService);
    }

    private Booking completedBookingWithWorker() {
        User customer = User.builder().userId(1L).build();
        Worker worker = Worker.builder().workerId(2L).build();
        return Booking.builder().bookingId(4L).status(BookingStatus.completed)
                .customer(customer).worker(worker).build();
    }

    @Test
    void submitReview_throwsBookingNotFoundException_whenBookingDoesNotExist() {
        when(bookingRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThrows(BookingNotFoundException.class,
                () -> service.submitReview(999L, new SubmitReviewRequest(5, "great")));
    }

    @Test
    void submitReview_throwsReviewNotAllowedException_whenBookingNotCompleted() {
        Booking pending = Booking.builder().bookingId(4L).status(BookingStatus.pending).build();
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(pending));

        assertThrows(ReviewNotAllowedException.class,
                () -> service.submitReview(4L, new SubmitReviewRequest(5, "great")));
    }

    @Test
    void submitReview_throwsReviewNotAllowedException_whenAlreadyReviewed() {
        Booking completed = completedBookingWithWorker();
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(completed));
        when(reviewRepository.findByBooking_BookingId(4L)).thenReturn(Optional.of(new Review()));

        assertThrows(ReviewNotAllowedException.class,
                () -> service.submitReview(4L, new SubmitReviewRequest(5, "great")));
    }

    @Test
    void submitReview_throwsBookingValidationException_whenRatingOutOfRange() {
        Booking completed = completedBookingWithWorker();
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(completed));
        when(reviewRepository.findByBooking_BookingId(4L)).thenReturn(Optional.empty());

        assertThrows(BookingValidationException.class,
                () -> service.submitReview(4L, new SubmitReviewRequest(6, "great")));
    }

    @Test
    void submitReview_succeeds_whenCompletedAndNotYetReviewed() {
        Booking completed = completedBookingWithWorker();
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(completed));
        when(reviewRepository.findByBooking_BookingId(4L)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setReviewId(50L);
            return r;
        });

        ReviewResponse response = service.submitReview(4L, new SubmitReviewRequest(5, "great"));

        assertEquals(50L, response.reviewId());
        assertEquals(4L, response.bookingId());
        assertEquals((short) 5, response.rating());
    }

    @Test
    void submitReview_withCaller_throwsForbidden_whenNotBookingCustomer() {
        Booking completed = completedBookingWithWorker();
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(completed));
        AuthenticatedUser stranger = new AuthenticatedUser(999L, "customer");

        assertThrows(ForbiddenException.class,
                () -> service.submitReview(4L, new SubmitReviewRequest(5, "great"), stranger));
    }

    @Test
    void submitReview_withCaller_succeeds_forBookingCustomer() {
        Booking completed = completedBookingWithWorker();
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(completed));
        when(reviewRepository.findByBooking_BookingId(4L)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setReviewId(50L);
            return r;
        });
        AuthenticatedUser owner = new AuthenticatedUser(1L, "customer");

        ReviewResponse response = service.submitReview(4L, new SubmitReviewRequest(5, "great"), owner);

        assertEquals(50L, response.reviewId());
    }
}
