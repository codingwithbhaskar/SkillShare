package com.skillshare.skillsharebackend.evaluation;

import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.Location;
import com.skillshare.skillsharebackend.domain.Review;
import com.skillshare.skillsharebackend.domain.Service;
import com.skillshare.skillsharebackend.domain.Skill;
import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.domain.WorkerAvailability;
import com.skillshare.skillsharebackend.domain.WorkerService;
import com.skillshare.skillsharebackend.domain.WorkerSkill;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import com.skillshare.skillsharebackend.domain.enums.BookingStatus;
import com.skillshare.skillsharebackend.domain.enums.BookingUrgency;
import com.skillshare.skillsharebackend.domain.enums.ProficiencyLevel;
import com.skillshare.skillsharebackend.domain.enums.UserRole;
import com.skillshare.skillsharebackend.domain.ids.WorkerServiceId;
import com.skillshare.skillsharebackend.domain.ids.WorkerSkillId;
import com.skillshare.skillsharebackend.repository.BookingRepository;
import com.skillshare.skillsharebackend.repository.LocationRepository;
import com.skillshare.skillsharebackend.repository.ReviewRepository;
import com.skillshare.skillsharebackend.repository.ServiceRepository;
import com.skillshare.skillsharebackend.repository.SkillRepository;
import com.skillshare.skillsharebackend.repository.UserRepository;
import com.skillshare.skillsharebackend.repository.WorkerAvailabilityRepository;
import com.skillshare.skillsharebackend.repository.WorkerRepository;
import com.skillshare.skillsharebackend.repository.WorkerServiceRepository;
import com.skillshare.skillsharebackend.repository.WorkerSkillRepository;
import com.skillshare.skillsharebackend.stats.WorkerStatsRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Phase 10 (Step 11) — self-contained synthetic data for the baseline
 * comparison evaluation harness ({@link AllocationEvaluationService}).
 * Unlike Step 10's benchmark harness (raw SQL, a separate database, run
 * via psql because this sandbox has no network path to Postgres), this
 * seeds through ordinary JPA/repository calls against the SAME
 * `skillshare_dev` database the app already runs against, tagged with a
 * unique {@code runTag} so it never collides with real or previous eval
 * data and is trivially identifiable later (emails like {@code
 * eval.worker.7.<runTag>@skillshare.eval}).
 *
 * <p>Reuses the 3 services / 6 skills V5__seed.sql already seeded (looked
 * up by name — never assumes their IDs) rather than creating duplicates.
 *
 * <p><b>Why every worker gets one real, reviewed history booking</b>:
 * {@code fn_score_candidate} substitutes a 0.6 cold-start rating score for
 * ANY worker with zero reviews (avg_rating COALESCEs to 0 in {@code
 * mv_worker_stats} — 01_schema_v3.sql). A freshly-created worker with no
 * bookings at all would compare identically to every other fresh worker on
 * rating, making {@code RatingOnlyStrategy}/{@code SimpleWeightedStrategy}
 * unable to differentiate anyone by rating — the whole point of those two
 * arms existing. So each worker gets exactly one already-completed,
 * already-reviewed booking (rating deterministically varied 2-5) BEFORE
 * the evaluation batch is generated, and {@link WorkerStatsRefreshService}
 * is called once afterward so {@code mv_worker_stats} reflects it.
 */
@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Slf4j
public class AllocationEvaluationSeeder {

    /** Same Nashik-area center and ~50km scatter box as Step 10's SQL
     *  generator (benchmark/sql/01_generate_synthetic_data.sql) - kept
     *  consistent across both pieces of synthetic-data infrastructure. */
    private static final double CENTER_LAT = 19.997454;
    private static final double CENTER_LON = 73.789803;
    private static final double LAT_SPAN_DEG = 0.9;
    private static final double LON_SPAN_DEG = 0.9 * 1.064;

    private static final String[] SERVICE_NAMES = {"Electrical Repair", "Plumbing", "Appliance Repair"};
    private static final String[] SKILL_NAMES = {
            "Wiring", "Fuse Box Repair", "Pipe Fitting", "Drain Cleaning", "AC Repair", "Furniture Assembly"
    };
    private static final ZoneOffset MARKET_OFFSET = ZoneOffset.of("+05:30"); // Asia/Kolkata, no DST

    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final WorkerRepository workerRepository;
    private final WorkerAvailabilityRepository workerAvailabilityRepository;
    private final WorkerSkillRepository workerSkillRepository;
    private final WorkerServiceRepository workerServiceRepository;
    private final ServiceRepository serviceRepository;
    private final SkillRepository skillRepository;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final WorkerStatsRefreshService workerStatsRefreshService;

    public record SeedResult(
            String runTag,
            int workersCreated,
            int customersCreated,
            List<Booking> evaluationBookings) {
    }

    @Transactional
    public SeedResult seed(int workerCount, int bookingCount) {
        String runTag = Long.toString(System.currentTimeMillis(), 36);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        List<Service> services = new ArrayList<>();
        for (String name : SERVICE_NAMES) {
            services.add(serviceRepository.findByServiceName(name)
                    .orElseThrow(() -> new IllegalStateException(
                            "Reference service '" + name + "' not found - is V5__seed.sql applied to this database?")));
        }
        List<Skill> skills = new ArrayList<>();
        for (String name : SKILL_NAMES) {
            skills.add(skillRepository.findBySkillName(name)
                    .orElseThrow(() -> new IllegalStateException(
                            "Reference skill '" + name + "' not found - is V5__seed.sql applied to this database?")));
        }

        // ---- shared customer pool (reused by both the rating-seed
        // bookings and the fresh evaluation bookings) ----
        int customerCount = Math.max(10, bookingCount / 5);
        List<User> customers = new ArrayList<>();
        List<Location> customerLocations = new ArrayList<>();
        for (int i = 1; i <= customerCount; i++) {
            Location loc = locationRepository.save(randomLocation(rnd, "Eval customer address " + i, runTag));
            User customer = userRepository.save(User.builder()
                    .role(UserRole.customer)
                    .fullName("Eval Customer " + i)
                    .email(String.format(Locale.ROOT, "eval.customer.%d.%s@skillshare.eval", i, runTag))
                    .passwordHash("eval_placeholder_hash")
                    .status(AccountStatus.active)
                    .build());
            customers.add(customer);
            customerLocations.add(loc);
        }

        // ---- workers, each with a service, a couple of skills, full
        // availability, and one reviewed history booking ----
        for (int g = 1; g <= workerCount; g++) {
            Location workerLocation = locationRepository.save(randomLocation(rnd, "Eval worker address " + g, runTag));
            User workerUser = userRepository.save(User.builder()
                    .role(UserRole.worker)
                    .fullName("Eval Worker " + g)
                    .email(String.format(Locale.ROOT, "eval.worker.%d.%s@skillshare.eval", g, runTag))
                    .passwordHash("eval_placeholder_hash")
                    .status(AccountStatus.active)
                    .build());

            Worker worker = workerRepository.save(Worker.builder()
                    .user(workerUser)
                    .location(workerLocation)
                    .bio("Synthetic evaluation worker")
                    .experienceYears((short) (g % 15))
                    .baseHourlyRate(BigDecimal.valueOf(250 + (g % 40) * 10))
                    .status(AccountStatus.active)
                    .build());

            for (short dow = 0; dow <= 6; dow++) {
                workerAvailabilityRepository.save(WorkerAvailability.builder()
                        .worker(worker)
                        .dayOfWeek(dow)
                        .startTime(LocalTime.of(8, 0))
                        .endTime(LocalTime.of(20, 0))
                        .build());
            }

            Skill skillA = skills.get((g - 1) % skills.size());
            Skill skillB = skills.get(g % skills.size());
            workerSkillRepository.save(WorkerSkill.builder()
                    .id(new WorkerSkillId(worker.getWorkerId(), skillA.getSkillId()))
                    .worker(worker).skill(skillA)
                    .proficiencyLevel(ProficiencyLevel.intermediate)
                    .build());
            if (!skillB.getSkillId().equals(skillA.getSkillId())) {
                workerSkillRepository.save(WorkerSkill.builder()
                        .id(new WorkerSkillId(worker.getWorkerId(), skillB.getSkillId()))
                        .worker(worker).skill(skillB)
                        .proficiencyLevel(ProficiencyLevel.beginner)
                        .build());
            }

            Service offeredService = services.get((g - 1) % services.size());
            workerServiceRepository.save(WorkerService.builder()
                    .id(new WorkerServiceId(worker.getWorkerId(), offeredService.getServiceId()))
                    .worker(worker).service(offeredService)
                    .hourlyRate(BigDecimal.valueOf(250 + (g % 50) * 5))
                    .build());

            // One already-completed, already-reviewed history booking so
            // this worker has a real (non-cold-start) avg_rating - see
            // class javadoc. Safely in the past; can never overlap with
            // the fresh evaluation bookings created below.
            User historyCustomer = customers.get(g % customers.size());
            Location historyCustomerLoc = customerLocations.get(g % customers.size());
            OffsetDateTime histStart = OffsetDateTime.of(
                    LocalDate.now().minusDays(30 + (g % 60)), LocalTime.of(9, 0), MARKET_OFFSET);
            Booking historyBooking = bookingRepository.save(Booking.builder()
                    .customer(historyCustomer)
                    .worker(worker)
                    .service(offeredService)
                    .location(historyCustomerLoc)
                    .status(BookingStatus.completed)
                    .urgency(BookingUrgency.normal)
                    .scheduledStart(histStart)
                    .scheduledEnd(histStart.plusHours(2))
                    .totalAmount(BigDecimal.valueOf(500 + (g % 40) * 10))
                    .build());

            short rating = (short) (2 + (g % 4)); // deterministically spreads 2..5 across workers
            reviewRepository.save(Review.builder()
                    .booking(historyBooking)
                    .customer(historyCustomer)
                    .worker(worker)
                    .rating(rating)
                    .comment("Synthetic evaluation seed rating")
                    .build());
        }

        // mv_worker_stats must reflect the reviews just inserted BEFORE
        // any strategy reads avg_rating for the evaluation batch below -
        // see WorkerStatsRefreshService's own javadoc on why a synchronous
        // refresh right after a rating-affecting write is the established
        // pattern (BookingService/ReviewService already do this).
        workerStatsRefreshService.refresh();

        // ---- fresh, unassigned pending bookings: the actual evaluation
        // batch every strategy will be asked to allocate ----
        List<Booking> evaluationBookings = new ArrayList<>(bookingCount);
        for (int i = 1; i <= bookingCount; i++) {
            User customer = customers.get(i % customers.size());
            Location customerLoc = customerLocations.get(i % customers.size());
            Service service = services.get(i % services.size());
            BookingUrgency urgency = (i % 5 == 0) ? BookingUrgency.urgent : BookingUrgency.normal;

            OffsetDateTime start = OffsetDateTime.of(
                    LocalDate.now().plusDays(7 + (i % 20)), LocalTime.of(10, 0), MARKET_OFFSET);

            Booking booking = bookingRepository.save(Booking.builder()
                    .customer(customer)
                    .worker(null)
                    .service(service)
                    .location(customerLoc)
                    .status(BookingStatus.pending)
                    .urgency(urgency)
                    .scheduledStart(start)
                    .scheduledEnd(start.plusHours(2))
                    .notes("Synthetic evaluation booking")
                    .build());
            evaluationBookings.add(booking);
        }

        log.info("AllocationEvaluationSeeder: seeded runTag={} workers={} customers={} evaluationBookings={}",
                runTag, workerCount, customerCount, bookingCount);

        return new SeedResult(runTag, workerCount, customerCount, evaluationBookings);
    }

    private Location randomLocation(ThreadLocalRandom rnd, String addressLine, String runTag) {
        double lat = CENTER_LAT + (rnd.nextDouble() - 0.5) * LAT_SPAN_DEG;
        double lon = CENTER_LON + (rnd.nextDouble() - 0.5) * LON_SPAN_DEG;
        return Location.builder()
                .addressLine(addressLine + " (" + runTag + ")")
                .city("Nashik")
                .state("Maharashtra")
                .pincode("422" + (100 + rnd.nextInt(900)))
                .latitude(BigDecimal.valueOf(lat).setScale(6, java.math.RoundingMode.HALF_UP))
                .longitude(BigDecimal.valueOf(lon).setScale(6, java.math.RoundingMode.HALF_UP))
                .build();
    }
}
