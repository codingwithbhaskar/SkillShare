package com.skillshare.skillsharebackend.booking;

import com.skillshare.skillsharebackend.allocation.BookingNotFoundException;
import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.BookingSkill;
import com.skillshare.skillsharebackend.domain.Location;
import com.skillshare.skillsharebackend.domain.Skill;
import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.domain.WorkerStats;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import com.skillshare.skillsharebackend.domain.enums.BookingStatus;
import com.skillshare.skillsharebackend.domain.enums.BookingUrgency;
import com.skillshare.skillsharebackend.domain.enums.UserRole;
import com.skillshare.skillsharebackend.domain.ids.BookingSkillId;
import com.skillshare.skillsharebackend.repository.BookingRepository;
import com.skillshare.skillsharebackend.repository.BookingSkillRepository;
import com.skillshare.skillsharebackend.repository.LocationRepository;
import com.skillshare.skillsharebackend.repository.ReviewRepository;
import com.skillshare.skillsharebackend.repository.ServiceRepository;
import com.skillshare.skillsharebackend.repository.SkillRepository;
import com.skillshare.skillsharebackend.repository.UserRepository;
import com.skillshare.skillsharebackend.repository.WorkerStatsRepository;
import com.skillshare.skillsharebackend.security.AuthenticatedUser;
import com.skillshare.skillsharebackend.security.ForbiddenException;
import com.skillshare.skillsharebackend.stats.WorkerStatsRefreshService;
import com.skillshare.skillsharebackend.web.dto.AssignedWorkerResponse;
import com.skillshare.skillsharebackend.web.dto.BookingResponse;
import com.skillshare.skillsharebackend.web.dto.CreateBookingRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Phase 5 - booking lifecycle. {@link #createBooking(CreateBookingRequest)}
 * is the piece every earlier phase's tests had to work around with a
 * manual SQL insert (see dev-status-and-next-steps.md's Phase 4 section);
 * the three transition methods below it (cancel/start/complete) are the
 * only other ways a booking's status column changes going forward,
 * alongside {@code sp_allocate_worker}'s own pending -> confirmed write
 * (Phase 4).
 *
 * <p>None of these methods create {@code notifications} rows themselves -
 * {@code trg_notify_booking_status_change} (04_triggers_v3.sql) fires on
 * every {@code UPDATE OF status ON bookings} no matter which code path
 * caused it, specifically so a notification can never be forgotten by a
 * new Java code path. Simply writing the new status and letting Postgres
 * see the UPDATE is enough.
 *
 * <p>Booking creation deliberately does NOT auto-allocate a worker - see
 * phase-5-in-progress-notes.md's explicit rationale. Allocation stays the
 * separate {@code POST /api/allocation/bookings/{id}/allocate} call
 * (Phase 4).
 *
 * <p><b>Ownership overloads (added in the Phase 8 full-retest follow-up):
 * </b> every public method here has a plain form (used directly by unit
 * tests, and internally) and an {@link AuthenticatedUser}-aware overload
 * (used by {@code BookingController}) that checks the caller is actually
 * entitled to the booking before delegating to the plain form - closing
 * the "existing endpoints don't yet cross-check client-supplied ids
 * against the authenticated principal" gap documented since Phase 7. See
 * {@link #requireCustomerOrAdmin}/{@link #requireAssignedWorkerOrAdmin}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingSkillRepository bookingSkillRepository;
    private final LocationRepository locationRepository;
    private final ServiceRepository serviceRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final WorkerStatsRefreshService workerStatsRefreshService;
    private final WorkerStatsRepository workerStatsRepository;

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
        User customer = userRepository.findById(request.customerId())
                .orElseThrow(() -> new BookingValidationException(
                        "No customer found with id " + request.customerId()));
        if (customer.getRole() != UserRole.customer) {
            throw new BookingValidationException(
                    "User " + request.customerId() + " is not a customer (role is " + customer.getRole() + ")");
        }
        if (customer.getStatus() != AccountStatus.active) {
            throw new BookingValidationException(
                    "This account is " + customer.getStatus() + " and cannot create bookings");
        }

        // Fully-qualified rather than imported: this file also needs
        // org.springframework.stereotype.Service for the class-level
        // @Service annotation, and the two types share a simple name.
        com.skillshare.skillsharebackend.domain.Service service = serviceRepository.findById(request.serviceId())
                .orElseThrow(() -> new BookingValidationException(
                        "No service found with id " + request.serviceId()));

        if (request.scheduledStart() == null || request.scheduledEnd() == null) {
            throw new BookingValidationException("scheduledStart and scheduledEnd are both required");
        }
        // Also DB-enforced via ck_booking_time_order, but a friendly 400
        // beats a raw constraint-violation stack trace.
        if (!request.scheduledEnd().isAfter(request.scheduledStart())) {
            throw new BookingValidationException("scheduledEnd must be after scheduledStart");
        }
        if (!request.scheduledStart().isAfter(OffsetDateTime.now())) {
            throw new BookingValidationException("scheduledStart must be in the future");
        }

        if (request.skillIds() == null || request.skillIds().isEmpty()) {
            throw new BookingValidationException("At least one skill id is required");
        }
        List<Skill> skills = new ArrayList<>();
        for (Long skillId : request.skillIds()) {
            skills.add(skillRepository.findById(skillId)
                    .orElseThrow(() -> new BookingValidationException("No skill found with id " + skillId)));
        }

        // bookings.location_id is a per-booking job address, not the
        // customer's or a reused worker location - see
        // location-map-feature-design.md - so creation always inserts a
        // fresh Location row alongside the Booking.
        Location location = locationRepository.save(Location.builder()
                .addressLine(request.addressLine())
                .landmark(request.landmark())
                .city(request.city())
                .state(request.state())
                .pincode(request.pincode())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build());

        Booking booking = bookingRepository.save(Booking.builder()
                .customer(customer)
                .service(service)
                .location(location)
                .status(BookingStatus.pending)
                .urgency(request.urgency() != null ? request.urgency() : BookingUrgency.normal)
                .scheduledStart(request.scheduledStart())
                .scheduledEnd(request.scheduledEnd())
                .notes(request.notes())
                .build());

        for (Skill skill : skills) {
            bookingSkillRepository.save(BookingSkill.builder()
                    .id(new BookingSkillId(booking.getBookingId(), skill.getSkillId()))
                    .booking(booking)
                    .skill(skill)
                    .build());
        }

        log.info("BookingService: created booking {} for customer {} ({} skill(s) requested)",
                booking.getBookingId(), customer.getUserId(), skills.size());

        return BookingResponse.from(booking);
    }

    /** Ignores/overrides a customer caller's {@code request.customerId()}
     *  with their own authenticated id - a customer can no longer create
     *  a booking "as" someone else just by putting a different id in the
     *  request body. An admin's request body is trusted as-is (an admin
     *  legitimately may be creating a booking on a customer's behalf). A
     *  worker caller is rejected outright - this app's booking flow has
     *  no concept of a worker booking themselves a job. */
    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request, AuthenticatedUser caller) {
        if (caller.isWorker()) {
            throw new ForbiddenException("Workers cannot create bookings");
        }
        CreateBookingRequest effective = caller.isAdmin()
                ? request
                : new CreateBookingRequest(
                        caller.userId(), request.serviceId(), request.skillIds(), request.urgency(),
                        request.scheduledStart(), request.scheduledEnd(), request.notes(),
                        request.addressLine(), request.landmark(), request.city(), request.state(),
                        request.pincode(), request.latitude(), request.longitude());
        return createBooking(effective);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsForCustomer(Long customerId) {
        return toResponsesWithReviewFlag(bookingRepository.findByCustomer_UserId(customerId));
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        return toResponseWithReviewFlag(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(Long bookingId, AuthenticatedUser caller) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        requireCustomerOrAssignedWorkerOrAdmin(booking, caller);
        return toResponseWithReviewFlag(booking);
    }

    @Transactional
    public BookingResponse cancelBooking(Long bookingId) {
        return finishCancel(lockBooking(bookingId));
    }

    @Transactional
    public BookingResponse cancelBooking(Long bookingId, AuthenticatedUser caller) {
        Booking booking = lockBooking(bookingId);
        requireCustomerOrAdmin(booking, caller);
        return finishCancel(booking);
    }

    @Transactional
    public BookingResponse startBooking(Long bookingId) {
        Booking booking = lockBooking(bookingId);
        if (booking.getStatus() != BookingStatus.confirmed) {
            throw new InvalidBookingTransitionException(bookingId, booking.getStatus(), "start", "confirmed");
        }
        booking.setStatus(BookingStatus.in_progress);
        bookingRepository.save(booking);
        log.info("BookingService: booking {} started", bookingId);
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse startBooking(Long bookingId, AuthenticatedUser caller) {
        Booking booking = lockBooking(bookingId);
        requireAssignedWorkerOrAdmin(booking, caller);
        if (booking.getStatus() != BookingStatus.confirmed) {
            throw new InvalidBookingTransitionException(bookingId, booking.getStatus(), "start", "confirmed");
        }
        booking.setStatus(BookingStatus.in_progress);
        bookingRepository.save(booking);
        log.info("BookingService: booking {} started", bookingId);
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse completeBooking(Long bookingId) {
        return finishComplete(lockBooking(bookingId));
    }

    @Transactional
    public BookingResponse completeBooking(Long bookingId, AuthenticatedUser caller) {
        Booking booking = lockBooking(bookingId);
        requireAssignedWorkerOrAdmin(booking, caller);
        return finishComplete(booking);
    }

    private BookingResponse finishCancel(Booking booking) {
        if (booking.getStatus() != BookingStatus.pending && booking.getStatus() != BookingStatus.confirmed) {
            throw new InvalidBookingTransitionException(booking.getBookingId(), booking.getStatus(), "cancel",
                    "pending or confirmed");
        }
        booking.setStatus(BookingStatus.cancelled);
        bookingRepository.save(booking);
        workerStatsRefreshService.refresh();
        log.info("BookingService: booking {} cancelled", booking.getBookingId());
        return BookingResponse.from(booking);
    }

    private BookingResponse finishComplete(Booking booking) {
        if (booking.getStatus() != BookingStatus.in_progress) {
            throw new InvalidBookingTransitionException(booking.getBookingId(), booking.getStatus(), "complete",
                    "in_progress");
        }
        booking.setStatus(BookingStatus.completed);
        bookingRepository.save(booking);
        workerStatsRefreshService.refresh();
        log.info("BookingService: booking {} completed", booking.getBookingId());
        return BookingResponse.from(booking);
    }

    private BookingResponse toResponseWithReviewFlag(Booking booking) {
        return toResponsesWithReviewFlag(List.of(booking)).get(0);
    }

    /**
     * Batched form of the above - one {@code reviews} query and one
     * {@code mv_worker_stats} query for the WHOLE list, not one of each
     * per booking. {@code getBookingsForCustomer} (a list endpoint) was
     * originally written as {@code .map(this::toResponseWithReviewFlag)},
     * which is a real N+1: up to 2N extra round trips for N bookings.
     * Harmless on localhost, but a genuine, measurable slowdown on a
     * cross-region deployment (browser -&gt; Render -&gt; Neon) - confirmed
     * live 2026-09-12. {@code getBooking}'s single-booking callers reuse
     * this too via {@link #toResponseWithReviewFlag}, just with a
     * one-element list (no regression, same query count as before for
     * that case).
     */
    private List<BookingResponse> toResponsesWithReviewFlag(List<Booking> bookings) {
        List<Long> completedBookingIds = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.completed)
                .map(Booking::getBookingId)
                .toList();
        Set<Long> reviewedBookingIds = completedBookingIds.isEmpty()
                ? Set.of()
                : new HashSet<>(reviewRepository.findBookingIdsWithReview(completedBookingIds));

        List<Long> workerIds = bookings.stream()
                .map(Booking::getWorker)
                .filter(Objects::nonNull)
                .map(Worker::getWorkerId)
                .distinct()
                .toList();
        Map<Long, WorkerStats> statsByWorkerId = workerIds.isEmpty()
                ? Map.of()
                : workerStatsRepository.findAllById(workerIds).stream()
                        .collect(Collectors.toMap(WorkerStats::getWorkerId, stats -> stats));

        return bookings.stream()
                .map(booking -> {
                    boolean reviewed = reviewedBookingIds.contains(booking.getBookingId());
                    AssignedWorkerResponse worker = booking.getWorker() != null
                            ? AssignedWorkerResponse.from(
                                    booking.getWorker(), statsByWorkerId.get(booking.getWorker().getWorkerId()))
                            : null;
                    return BookingResponse.from(booking, reviewed, worker);
                })
                .toList();
    }

    private void requireCustomerOrAdmin(Booking booking, AuthenticatedUser caller) {
        boolean isOwner = booking.getCustomer().getUserId().equals(caller.userId());
        if (!isOwner && !caller.isAdmin()) {
            throw new ForbiddenException("You do not have access to booking " + booking.getBookingId());
        }
    }

    private void requireAssignedWorkerOrAdmin(Booking booking, AuthenticatedUser caller) {
        boolean isAssignedWorker = booking.getWorker() != null
                && booking.getWorker().getUser().getUserId().equals(caller.userId());
        if (!isAssignedWorker && !caller.isAdmin()) {
            throw new ForbiddenException("You are not the worker assigned to booking " + booking.getBookingId());
        }
    }

    private void requireCustomerOrAssignedWorkerOrAdmin(Booking booking, AuthenticatedUser caller) {
        boolean isCustomer = booking.getCustomer().getUserId().equals(caller.userId());
        boolean isAssignedWorker = booking.getWorker() != null
                && booking.getWorker().getUser().getUserId().equals(caller.userId());
        if (!isCustomer && !isAssignedWorker && !caller.isAdmin()) {
            throw new ForbiddenException("You do not have access to booking " + booking.getBookingId());
        }
    }

    // Same pessimistic-lock pattern AllocationService.allocate established
    // in its Phase 4 post-review hardening: all four status-mutating
    // methods (allocate lives in AllocationService, the other three here)
    // touch the same bookings.status column under the same concurrency
    // concerns, so all of them take the row lock first.
    private Booking lockBooking(Long bookingId) {
        return bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }
}
