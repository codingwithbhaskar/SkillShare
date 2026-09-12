package com.skillshare.skillsharebackend.booking;

import com.skillshare.skillsharebackend.allocation.BookingNotFoundException;
import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.Location;
import com.skillshare.skillsharebackend.domain.Service;
import com.skillshare.skillsharebackend.domain.Skill;
import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.domain.WorkerStats;
import com.skillshare.skillsharebackend.domain.enums.BookingStatus;
import com.skillshare.skillsharebackend.domain.enums.BookingUrgency;
import com.skillshare.skillsharebackend.domain.enums.UserRole;
import com.skillshare.skillsharebackend.repository.BookingRepository;
import com.skillshare.skillsharebackend.repository.BookingSkillRepository;
import com.skillshare.skillsharebackend.repository.LocationRepository;
import com.skillshare.skillsharebackend.repository.ServiceRepository;
import com.skillshare.skillsharebackend.repository.SkillRepository;
import com.skillshare.skillsharebackend.repository.ReviewRepository;
import com.skillshare.skillsharebackend.repository.UserRepository;
import com.skillshare.skillsharebackend.repository.WorkerStatsRepository;
import com.skillshare.skillsharebackend.security.AuthenticatedUser;
import com.skillshare.skillsharebackend.security.ForbiddenException;
import com.skillshare.skillsharebackend.stats.WorkerStatsRefreshService;
import com.skillshare.skillsharebackend.web.dto.BookingResponse;
import com.skillshare.skillsharebackend.web.dto.CreateBookingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BookingService}. All six repositories are mocked
 * - same style as {@code AllocationServiceTest} - so these pin down the
 * service's own validation and status-transition-guard logic, not the
 * real Postgres round-trip (ck_booking_time_order, the exclusion
 * constraint, the notification triggers), which is exercised live per
 * dev-status-and-next-steps.md's verification bar.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingSkillRepository bookingSkillRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private ServiceRepository serviceRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private WorkerStatsRefreshService workerStatsRefreshService;
    @Mock
    private WorkerStatsRepository workerStatsRepository;

    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookingRepository, bookingSkillRepository, locationRepository,
                serviceRepository, skillRepository, userRepository, reviewRepository, workerStatsRefreshService,
                workerStatsRepository);
    }

    // BookingResponse.from() dereferences customer/service/location, so any
    // Booking a "succeeds" test expects to reach that mapping needs all three
    // set - unlike the "throws" tests below, which never get that far.
    private Booking fullBooking(BookingStatus status) {
        return Booking.builder()
                .bookingId(4L)
                .status(status)
                .customer(User.builder().userId(1L).build())
                .service(Service.builder().serviceId(2L).build())
                .location(Location.builder().locationId(10L).build())
                .build();
    }

    private Booking fullBookingWithWorker(BookingStatus status, Long workerUserId) {
        return Booking.builder()
                .bookingId(4L)
                .status(status)
                .customer(User.builder().userId(1L).build())
                .worker(Worker.builder()
                        .workerId(2L)
                        .user(User.builder().userId(workerUserId).fullName("Ravi Pawar").phone("9999999999").build())
                        .bio("Experienced electrician")
                        .experienceYears((short) 5)
                        .baseHourlyRate(new BigDecimal("300.00"))
                        .build())
                .service(Service.builder().serviceId(2L).build())
                .location(Location.builder().locationId(10L).build())
                .build();
    }

    private CreateBookingRequest validRequest() {
        return new CreateBookingRequest(
                1L, 2L, List.of(3L), BookingUrgency.normal,
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(2),
                "notes", "123 Main St", "near park", "Pune", "MH", "411001",
                new BigDecimal("18.520000"), new BigDecimal("73.850000"));
    }

    @Test
    void createBooking_throwsBookingValidationException_whenCustomerNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(BookingValidationException.class, () -> service.createBooking(validRequest()));
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void createBooking_throwsBookingValidationException_whenCustomerIsNotCustomerRole() {
        User worker = User.builder().userId(1L).role(UserRole.worker).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(worker));

        assertThrows(BookingValidationException.class, () -> service.createBooking(validRequest()));
    }

    @Test
    void createBooking_throwsBookingValidationException_whenCustomerSuspended() {
        User customer = User.builder().userId(1L).role(UserRole.customer).status(AccountStatus.suspended).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        assertThrows(BookingValidationException.class, () -> service.createBooking(validRequest()));
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void createBooking_throwsBookingValidationException_whenCustomerDeactivated() {
        User customer = User.builder().userId(1L).role(UserRole.customer).status(AccountStatus.inactive).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        assertThrows(BookingValidationException.class, () -> service.createBooking(validRequest()));
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void createBooking_throwsBookingValidationException_whenScheduledEndNotAfterStart() {
        User customer = User.builder().userId(1L).role(UserRole.customer).status(AccountStatus.active).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(serviceRepository.findById(2L)).thenReturn(Optional.of(Service.builder().serviceId(2L).build()));

        CreateBookingRequest bad = new CreateBookingRequest(
                1L, 2L, List.of(3L), BookingUrgency.normal,
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1),
                null, "addr", null, "city", "state", "pin", BigDecimal.ONE, BigDecimal.ONE);

        assertThrows(BookingValidationException.class, () -> service.createBooking(bad));
    }

    @Test
    void createBooking_throwsBookingValidationException_whenScheduledStartInPast() {
        User customer = User.builder().userId(1L).role(UserRole.customer).status(AccountStatus.active).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(serviceRepository.findById(2L)).thenReturn(Optional.of(Service.builder().serviceId(2L).build()));

        CreateBookingRequest bad = new CreateBookingRequest(
                1L, 2L, List.of(3L), BookingUrgency.normal,
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(1),
                null, "addr", null, "city", "state", "pin", BigDecimal.ONE, BigDecimal.ONE);

        assertThrows(BookingValidationException.class, () -> service.createBooking(bad));
    }

    @Test
    void createBooking_throwsBookingValidationException_whenNoSkillsRequested() {
        User customer = User.builder().userId(1L).role(UserRole.customer).status(AccountStatus.active).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(serviceRepository.findById(2L)).thenReturn(Optional.of(Service.builder().serviceId(2L).build()));

        CreateBookingRequest bad = new CreateBookingRequest(
                1L, 2L, List.of(), BookingUrgency.normal,
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(1),
                null, "addr", null, "city", "state", "pin", BigDecimal.ONE, BigDecimal.ONE);

        assertThrows(BookingValidationException.class, () -> service.createBooking(bad));
    }

    @Test
    void createBooking_throwsBookingValidationException_whenSkillIdUnknown() {
        User customer = User.builder().userId(1L).role(UserRole.customer).status(AccountStatus.active).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(serviceRepository.findById(2L)).thenReturn(Optional.of(Service.builder().serviceId(2L).build()));
        when(skillRepository.findById(3L)).thenReturn(Optional.empty());

        assertThrows(BookingValidationException.class, () -> service.createBooking(validRequest()));
    }

    @Test
    void createBooking_succeeds_savesLocationBookingAndSkills() {
        User customer = User.builder().userId(1L).role(UserRole.customer).status(AccountStatus.active).build();
        Service svc = Service.builder().serviceId(2L).build();
        Skill skill = Skill.builder().skillId(3L).skillName("Wiring").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(serviceRepository.findById(2L)).thenReturn(Optional.of(svc));
        when(skillRepository.findById(3L)).thenReturn(Optional.of(skill));

        Location savedLocation = Location.builder().locationId(10L).build();
        when(locationRepository.save(any(Location.class))).thenReturn(savedLocation);

        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setBookingId(99L);
            return b;
        });

        BookingResponse response = service.createBooking(validRequest());

        assertEquals(99L, response.bookingId());
        assertEquals(1L, response.customerId());
        assertEquals(2L, response.serviceId());
        assertEquals(10L, response.locationId());
        assertEquals(BookingStatus.pending, response.status());
        assertEquals(BookingUrgency.normal, response.urgency());
        verify(bookingSkillRepository).save(any());
    }

    @Test
    void cancelBooking_throwsBookingNotFoundException_whenBookingDoesNotExist() {
        when(bookingRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThrows(BookingNotFoundException.class, () -> service.cancelBooking(999L));
    }

    @Test
    void cancelBooking_throwsInvalidBookingTransitionException_whenAlreadyCompleted() {
        Booking completed = Booking.builder().bookingId(4L).status(BookingStatus.completed).build();
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(completed));

        assertThrows(InvalidBookingTransitionException.class, () -> service.cancelBooking(4L));
    }

    @Test
    void cancelBooking_succeeds_fromPending() {
        Booking pending = fullBooking(BookingStatus.pending);
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(pending));

        BookingResponse response = service.cancelBooking(4L);

        assertEquals(BookingStatus.cancelled, response.status());
        verify(bookingRepository).save(pending);
    }

    @Test
    void startBooking_throwsInvalidBookingTransitionException_whenNotConfirmed() {
        Booking pending = Booking.builder().bookingId(4L).status(BookingStatus.pending).build();
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(pending));

        assertThrows(InvalidBookingTransitionException.class, () -> service.startBooking(4L));
    }

    @Test
    void startBooking_succeeds_fromConfirmed() {
        Booking confirmed = fullBooking(BookingStatus.confirmed);
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(confirmed));

        BookingResponse response = service.startBooking(4L);

        assertEquals(BookingStatus.in_progress, response.status());
    }

    @Test
    void completeBooking_throwsInvalidBookingTransitionException_whenNotInProgress() {
        Booking confirmed = Booking.builder().bookingId(4L).status(BookingStatus.confirmed).build();
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(confirmed));

        assertThrows(InvalidBookingTransitionException.class, () -> service.completeBooking(4L));
    }

    @Test
    void completeBooking_succeeds_fromInProgress() {
        Booking inProgress = fullBooking(BookingStatus.in_progress);
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(inProgress));

        BookingResponse response = service.completeBooking(4L);

        assertEquals(BookingStatus.completed, response.status());
    }

    @Test
    void getBooking_withCaller_throwsForbidden_whenNotOwnerOrAssignedWorker() {
        Booking booking = fullBooking(BookingStatus.confirmed);
        when(bookingRepository.findById(4L)).thenReturn(Optional.of(booking));
        AuthenticatedUser stranger = new AuthenticatedUser(999L, "customer");

        assertThrows(ForbiddenException.class, () -> service.getBooking(4L, stranger));
    }

    @Test
    void getBooking_withCaller_succeeds_forOwningCustomer() {
        Booking booking = fullBooking(BookingStatus.confirmed);
        when(bookingRepository.findById(4L)).thenReturn(Optional.of(booking));
        AuthenticatedUser owner = new AuthenticatedUser(1L, "customer");

        BookingResponse response = service.getBooking(4L, owner);

        assertEquals(4L, response.bookingId());
    }

    @Test
    void getBooking_withCaller_succeeds_forAdmin() {
        Booking booking = fullBooking(BookingStatus.confirmed);
        when(bookingRepository.findById(4L)).thenReturn(Optional.of(booking));
        AuthenticatedUser admin = new AuthenticatedUser(42L, "admin");

        BookingResponse response = service.getBooking(4L, admin);

        assertEquals(4L, response.bookingId());
    }

    @Test
    void getBooking_worker_isNull_whenNoWorkerAllocatedYet() {
        Booking pending = fullBooking(BookingStatus.pending);
        when(bookingRepository.findById(4L)).thenReturn(Optional.of(pending));

        BookingResponse response = service.getBooking(4L);

        assertNull(response.worker());
        verifyNoInteractions(workerStatsRepository);
    }

    @Test
    void getBooking_includesAssignedWorkerDetails_withRating() {
        Booking confirmed = fullBookingWithWorker(BookingStatus.confirmed, 2L);
        when(bookingRepository.findById(4L)).thenReturn(Optional.of(confirmed));
        WorkerStats stats = new WorkerStats(2L, 12L, 10L, 1L, 8L, new BigDecimal("4.50"));
        when(workerStatsRepository.findById(2L)).thenReturn(Optional.of(stats));

        BookingResponse response = service.getBooking(4L);

        assertEquals(2L, response.worker().workerId());
        assertEquals("Ravi Pawar", response.worker().fullName());
        assertEquals("9999999999", response.worker().phone());
        assertEquals((short) 5, response.worker().experienceYears());
        assertEquals(new BigDecimal("300.00"), response.worker().baseHourlyRate());
        assertEquals(new BigDecimal("4.50"), response.worker().avgRating());
        assertEquals(8L, response.worker().reviewCount());
    }

    @Test
    void getBooking_includesAssignedWorkerDetails_evenWithNoStatsRowYet() {
        // A worker with zero completed jobs has no mv_worker_stats row -
        // the basic worker fields must still come through, with a null
        // rating rather than a NullPointerException.
        Booking confirmed = fullBookingWithWorker(BookingStatus.confirmed, 2L);
        when(bookingRepository.findById(4L)).thenReturn(Optional.of(confirmed));
        when(workerStatsRepository.findById(2L)).thenReturn(Optional.empty());

        BookingResponse response = service.getBooking(4L);

        assertEquals(2L, response.worker().workerId());
        assertEquals("Ravi Pawar", response.worker().fullName());
        assertNull(response.worker().avgRating());
    }

    @Test
    void cancelBooking_withCaller_throwsForbidden_whenNotOwner() {
        Booking pending = fullBooking(BookingStatus.pending);
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(pending));
        AuthenticatedUser stranger = new AuthenticatedUser(999L, "customer");

        assertThrows(ForbiddenException.class, () -> service.cancelBooking(4L, stranger));
    }

    @Test
    void cancelBooking_withCaller_succeeds_forOwningCustomer() {
        Booking pending = fullBooking(BookingStatus.pending);
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(pending));
        AuthenticatedUser owner = new AuthenticatedUser(1L, "customer");

        BookingResponse response = service.cancelBooking(4L, owner);

        assertEquals(BookingStatus.cancelled, response.status());
    }

    @Test
    void startBooking_withCaller_throwsForbidden_whenNotAssignedWorker() {
        Booking confirmed = fullBookingWithWorker(BookingStatus.confirmed, 2L);
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(confirmed));
        AuthenticatedUser stranger = new AuthenticatedUser(999L, "worker");

        assertThrows(ForbiddenException.class, () -> service.startBooking(4L, stranger));
    }

    @Test
    void startBooking_withCaller_succeeds_forAssignedWorker() {
        Booking confirmed = fullBookingWithWorker(BookingStatus.confirmed, 2L);
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(confirmed));
        AuthenticatedUser assignedWorker = new AuthenticatedUser(2L, "worker");

        BookingResponse response = service.startBooking(4L, assignedWorker);

        assertEquals(BookingStatus.in_progress, response.status());
    }

    @Test
    void completeBooking_withCaller_succeeds_forAssignedWorker() {
        Booking inProgress = fullBookingWithWorker(BookingStatus.in_progress, 2L);
        when(bookingRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(inProgress));
        AuthenticatedUser assignedWorker = new AuthenticatedUser(2L, "worker");

        BookingResponse response = service.completeBooking(4L, assignedWorker);

        assertEquals(BookingStatus.completed, response.status());
    }

    @Test
    void createBooking_withCustomerCaller_overridesCustomerIdToOwnId() {
        User customer = User.builder().userId(7L).role(UserRole.customer).status(AccountStatus.active).build();
        Service svc = Service.builder().serviceId(2L).build();
        Skill skill = Skill.builder().skillId(3L).skillName("Wiring").build();

        when(userRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(serviceRepository.findById(2L)).thenReturn(Optional.of(svc));
        when(skillRepository.findById(3L)).thenReturn(Optional.of(skill));

        Location savedLocation = Location.builder().locationId(10L).build();
        when(locationRepository.save(any(Location.class))).thenReturn(savedLocation);

        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setBookingId(100L);
            return b;
        });

        // Request body claims customerId=1L, but the authenticated caller
        // is user 7 - the overload must ignore the spoofed id rather than
        // trusting the request body, the exact gap this closes.
        CreateBookingRequest spoofed = validRequest();
        AuthenticatedUser caller = new AuthenticatedUser(7L, "customer");

        BookingResponse response = service.createBooking(spoofed, caller);

        assertEquals(7L, response.customerId());
    }

    @Test
    void createBooking_withWorkerCaller_throwsForbidden() {
        AuthenticatedUser worker = new AuthenticatedUser(1L, "worker");

        assertThrows(ForbiddenException.class, () -> service.createBooking(validRequest(), worker));
        verifyNoInteractions(bookingRepository);
    }
}
