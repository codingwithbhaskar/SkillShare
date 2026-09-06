package com.skillshare.skillsharebackend.allocation;

import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.BookingSkill;
import com.skillshare.skillsharebackend.domain.Location;
import com.skillshare.skillsharebackend.domain.Service;
import com.skillshare.skillsharebackend.domain.Skill;
import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.domain.WorkerAvailability;
import com.skillshare.skillsharebackend.domain.WorkerService;
import com.skillshare.skillsharebackend.domain.WorkerSkill;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import com.skillshare.skillsharebackend.domain.enums.BookingStatus;
import com.skillshare.skillsharebackend.domain.enums.BookingUrgency;
import com.skillshare.skillsharebackend.domain.ids.BookingSkillId;
import com.skillshare.skillsharebackend.domain.ids.WorkerSkillId;
import com.skillshare.skillsharebackend.repository.BookingRepository;
import com.skillshare.skillsharebackend.repository.BookingSkillRepository;
import com.skillshare.skillsharebackend.repository.WorkerAvailabilityRepository;
import com.skillshare.skillsharebackend.repository.WorkerServiceRepository;
import com.skillshare.skillsharebackend.repository.WorkerSkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TraditionalAllocator}'s filter chain (skill ->
 * availability -> schedule overlap -> nearest-by-distance). All five
 * injected repositories are mocked - this is a pure unit test of the
 * in-memory filtering/selection logic, not an integration test against a
 * real database. The real end-to-end proof (Hibernate mappings, Flyway
 * schema, the whole app wiring) is the confirmed `mvnw.cmd
 * spring-boot:run` run recorded in phase-2-domain-repository-layer.md -
 * these tests exist to pin down the allocator's own decision logic so a
 * future change to it fails fast, locally, without needing Postgres.
 */
@ExtendWith(MockitoExtension.class)
class TraditionalAllocatorTest {

    @Mock
    private BookingSkillRepository bookingSkillRepository;
    @Mock
    private WorkerServiceRepository workerServiceRepository;
    @Mock
    private WorkerSkillRepository workerSkillRepository;
    @Mock
    private WorkerAvailabilityRepository workerAvailabilityRepository;
    @Mock
    private BookingRepository bookingRepository;

    private TraditionalAllocator allocator;

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");

    private Service electricalService;
    private Skill wiringSkill;
    private OffsetDateTime scheduledStart;
    private OffsetDateTime scheduledEnd;
    private short dayOfWeek;
    private Booking booking;

    @BeforeEach
    void setUp() {
        allocator = new TraditionalAllocator(
                bookingSkillRepository, workerServiceRepository, workerSkillRepository,
                workerAvailabilityRepository, bookingRepository);

        electricalService = Service.builder().serviceId(100L).serviceName("Electrical Repair").build();
        wiringSkill = Skill.builder().skillId(10L).skillName("Wiring").build();

        Location bookingLocation = Location.builder()
                .locationId(1L)
                .addressLine("Booking Site")
                .city("Nashik").state("Maharashtra")
                .latitude(new BigDecimal("19.997454"))
                .longitude(new BigDecimal("73.789803"))
                .build();

        // Fixed instant, IST offset, so the allocator's Asia/Kolkata
        // conversion is a same-offset no-op here - keeps the day-of-week/
        // local-time math easy to reason about without re-deriving it by
        // hand for an arbitrary date. The conversion itself is already
        // exercised for real by fn_find_candidates in the SQL layer; what
        // these tests care about is the filter chain, not the timezone
        // arithmetic.
        scheduledStart = OffsetDateTime.of(2026, 9, 7, 10, 0, 0, 0, ZoneOffset.of("+05:30"));
        scheduledEnd = scheduledStart.plusHours(2);

        ZonedDateTime startLocal = scheduledStart.atZoneSameInstant(MARKET_ZONE);
        // java.time.DayOfWeek: MONDAY=1..SUNDAY=7; Postgres EXTRACT(DOW): SUNDAY=0..SATURDAY=6.
        dayOfWeek = (short) (startLocal.getDayOfWeek().getValue() % 7);

        booking = Booking.builder()
                .bookingId(4L)
                .service(electricalService)
                .location(bookingLocation)
                .status(BookingStatus.pending)
                .urgency(BookingUrgency.normal)
                .scheduledStart(scheduledStart)
                .scheduledEnd(scheduledEnd)
                .build();

        when(bookingSkillRepository.findByBooking_BookingId(booking.getBookingId()))
                .thenReturn(List.of(bookingSkillFor(booking, wiringSkill)));
    }

    @Test
    void picksNearestEligibleWorker_whenMultipleQualify() {
        Worker near = eligibleWorker(1L, "19.999000", "73.800000"); // ~1 km from the booking site
        Worker far = eligibleWorker(2L, "18.520430", "73.856743");  // Pune, ~165 km away

        givenWorkerIsFullyEligible(near);
        givenWorkerIsFullyEligible(far);
        when(workerServiceRepository.findByService_ServiceId(electricalService.getServiceId()))
                .thenReturn(List.of(offeringFor(near), offeringFor(far)));

        Optional<Worker> result = allocator.selectWorker(booking);

        assertTrue(result.isPresent());
        assertEquals(near.getWorkerId(), result.get().getWorkerId());
    }

    @Test
    void excludesWorker_missingARequiredSkill() {
        Worker skilled = eligibleWorker(1L, "19.999000", "73.800000");
        Worker unskilled = eligibleWorker(2L, "19.998000", "73.790000"); // closer, but missing the skill

        givenWorkerIsFullyEligible(skilled);
        // unskilled fails the hard skill filter, so the loop `continue`s
        // before ever calling the availability/overlap repositories for
        // this worker - only the skill lookup needs stubbing.
        when(workerSkillRepository.findByWorker_WorkerId(unskilled.getWorkerId()))
                .thenReturn(Collections.emptyList());

        when(workerServiceRepository.findByService_ServiceId(electricalService.getServiceId()))
                .thenReturn(List.of(offeringFor(skilled), offeringFor(unskilled)));

        Optional<Worker> result = allocator.selectWorker(booking);

        assertTrue(result.isPresent());
        assertEquals(skilled.getWorkerId(), result.get().getWorkerId());
    }

    @Test
    void excludesWorker_notAvailableAtBookingTime() {
        Worker unavailable = eligibleWorker(1L, "19.999000", "73.800000");

        when(workerSkillRepository.findByWorker_WorkerId(unavailable.getWorkerId()))
                .thenReturn(List.of(workerSkillFor(unavailable, wiringSkill)));
        // Has the skill, but no availability slot covering the booking's
        // day-of-week at all (the derived-query repository method already
        // filters by day server-side, so "no matching row" is what an
        // unavailable worker looks like from the allocator's perspective).
        when(workerAvailabilityRepository.findByWorker_WorkerIdAndDayOfWeek(unavailable.getWorkerId(), dayOfWeek))
                .thenReturn(Collections.emptyList());

        when(workerServiceRepository.findByService_ServiceId(electricalService.getServiceId()))
                .thenReturn(List.of(offeringFor(unavailable)));

        Optional<Worker> result = allocator.selectWorker(booking);

        assertFalse(result.isPresent());
    }

    @Test
    void excludesWorker_withOverlappingBooking() {
        Worker busy = eligibleWorker(1L, "19.999000", "73.800000");

        when(workerSkillRepository.findByWorker_WorkerId(busy.getWorkerId()))
                .thenReturn(List.of(workerSkillFor(busy, wiringSkill)));
        when(workerAvailabilityRepository.findByWorker_WorkerIdAndDayOfWeek(busy.getWorkerId(), dayOfWeek))
                .thenReturn(List.of(availabilitySlot(busy)));
        when(bookingRepository.findOverlapping(busy.getWorkerId(), booking.getBookingId(), scheduledStart, scheduledEnd, BookingStatus.cancelled))
                .thenReturn(List.of(Booking.builder().bookingId(99L).build())); // already has a conflicting job

        when(workerServiceRepository.findByService_ServiceId(electricalService.getServiceId()))
                .thenReturn(List.of(offeringFor(busy)));

        Optional<Worker> result = allocator.selectWorker(booking);

        assertFalse(result.isPresent());
    }

    @Test
    void excludesInactiveWorker_andWorkerWithNoLocation() {
        Worker inactive = Worker.builder()
                .workerId(1L).status(AccountStatus.suspended)
                .location(locationAt("19.999000", "73.800000"))
                .build();
        Worker noLocation = Worker.builder()
                .workerId(2L).status(AccountStatus.active)
                .location(null)
                .build();

        // Both fail before the skill/availability/overlap repositories are
        // ever consulted, so nothing else needs stubbing here.
        when(workerServiceRepository.findByService_ServiceId(electricalService.getServiceId()))
                .thenReturn(List.of(offeringFor(inactive), offeringFor(noLocation)));

        Optional<Worker> result = allocator.selectWorker(booking);

        assertFalse(result.isPresent());
    }

    @Test
    void returnsEmpty_whenNoWorkerOffersTheService() {
        when(workerServiceRepository.findByService_ServiceId(electricalService.getServiceId()))
                .thenReturn(Collections.emptyList());

        Optional<Worker> result = allocator.selectWorker(booking);

        assertFalse(result.isPresent());
    }

    // --- fixture helpers -------------------------------------------------

    private Worker eligibleWorker(Long id, String lat, String lon) {
        return Worker.builder()
                .workerId(id)
                .status(AccountStatus.active)
                .location(locationAt(lat, lon))
                .build();
    }

    private Location locationAt(String lat, String lon) {
        return Location.builder()
                .locationId(1000L)
                .addressLine("Worker Base")
                .city("Nashik").state("Maharashtra")
                .latitude(new BigDecimal(lat))
                .longitude(new BigDecimal(lon))
                .build();
    }

    private WorkerService offeringFor(Worker worker) {
        return WorkerService.builder()
                .worker(worker)
                .service(electricalService)
                .hourlyRate(new BigDecimal("500.00"))
                .build();
    }

    private WorkerSkill workerSkillFor(Worker worker, Skill skill) {
        return WorkerSkill.builder()
                .id(new WorkerSkillId())
                .worker(worker)
                .skill(skill)
                .build();
    }

    private BookingSkill bookingSkillFor(Booking booking, Skill skill) {
        return BookingSkill.builder()
                .id(new BookingSkillId())
                .booking(booking)
                .skill(skill)
                .build();
    }

    private WorkerAvailability availabilitySlot(Worker worker) {
        return WorkerAvailability.builder()
                .availabilityId(900L)
                .worker(worker)
                .dayOfWeek(dayOfWeek)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(18, 0))
                .build();
    }

    /** Wires up the "everything checks out" stubs for a worker: has the
     *  required skill, is available at the booking's day/time, and has
     *  no conflicting bookings. */
    private void givenWorkerIsFullyEligible(Worker worker) {
        when(workerSkillRepository.findByWorker_WorkerId(worker.getWorkerId()))
                .thenReturn(List.of(workerSkillFor(worker, wiringSkill)));
        when(workerAvailabilityRepository.findByWorker_WorkerIdAndDayOfWeek(worker.getWorkerId(), dayOfWeek))
                .thenReturn(List.of(availabilitySlot(worker)));
        when(bookingRepository.findOverlapping(worker.getWorkerId(), booking.getBookingId(), scheduledStart, scheduledEnd, BookingStatus.cancelled))
                .thenReturn(Collections.emptyList());
    }
}
