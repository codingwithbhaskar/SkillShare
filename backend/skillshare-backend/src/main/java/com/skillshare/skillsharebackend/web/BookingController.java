package com.skillshare.skillsharebackend.web;

import com.skillshare.skillsharebackend.booking.BookingService;
import com.skillshare.skillsharebackend.booking.ReviewService;
import com.skillshare.skillsharebackend.security.AuthenticatedUser;
import com.skillshare.skillsharebackend.web.dto.BookingResponse;
import com.skillshare.skillsharebackend.web.dto.CreateBookingRequest;
import com.skillshare.skillsharebackend.web.dto.ReviewResponse;
import com.skillshare.skillsharebackend.web.dto.SubmitReviewRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phase 5 - the booking lifecycle's HTTP surface. Same thin-controller
 * split as {@link AllocationController}: all logic lives in
 * {@link BookingService}/{@link ReviewService}, which is what's
 * unit-tested.
 *
 * <p>Every endpoint below now passes an {@link AuthenticatedUser} into
 * its service call - the ownership-checked overloads added in the Phase
 * 8 full-retest follow-up (see {@code BookingService}'s javadoc). This
 * closes the "any authenticated user can look up/cancel/etc. any booking"
 * gap documented since Phase 7.
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final ReviewService reviewService;

    /** Creates a booking as {@code pending} - does NOT allocate a worker.
     *  See {@link BookingService}'s javadoc for why. A customer caller's
     *  {@code customerId} in the request body is ignored in favor of
     *  their own authenticated id - see
     *  {@code BookingService#createBooking(CreateBookingRequest,
     *  AuthenticatedUser)}. */
    @PostMapping
    public BookingResponse createBooking(@RequestBody CreateBookingRequest request, Authentication authentication) {
        return bookingService.createBooking(request, AuthenticatedUser.from(authentication));
    }

    /** The authenticated user's own bookings (as customer) - added for
     *  Phase 8's "My Bookings" list. {@code authentication.getName()} is
     *  the JWT subject (userId as a String) - see JwtAuthenticationFilter's
     *  javadoc for why the principal is a bare id, not a re-fetched User.
     *  No separate ownership check needed here - the customerId comes
     *  from the token, never the request. */
    @GetMapping("/me")
    public List<BookingResponse> myBookings(Authentication authentication) {
        return bookingService.getBookingsForCustomer(Long.valueOf(authentication.getName()));
    }

    /** Single booking detail - added for Phase 8's booking-detail/payment
     *  page. Restricted to the booking's own customer, its assigned
     *  worker, or an admin. */
    @GetMapping("/{bookingId}")
    public BookingResponse getBooking(@PathVariable Long bookingId, Authentication authentication) {
        return bookingService.getBooking(bookingId, AuthenticatedUser.from(authentication));
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancel(@PathVariable Long bookingId, Authentication authentication) {
        return bookingService.cancelBooking(bookingId, AuthenticatedUser.from(authentication));
    }

    @PostMapping("/{bookingId}/start")
    public BookingResponse start(@PathVariable Long bookingId, Authentication authentication) {
        return bookingService.startBooking(bookingId, AuthenticatedUser.from(authentication));
    }

    @PostMapping("/{bookingId}/complete")
    public BookingResponse complete(@PathVariable Long bookingId, Authentication authentication) {
        return bookingService.completeBooking(bookingId, AuthenticatedUser.from(authentication));
    }

    @PostMapping("/{bookingId}/review")
    public ReviewResponse review(
            @PathVariable Long bookingId, @RequestBody SubmitReviewRequest request, Authentication authentication) {
        return reviewService.submitReview(bookingId, request, AuthenticatedUser.from(authentication));
    }
}
