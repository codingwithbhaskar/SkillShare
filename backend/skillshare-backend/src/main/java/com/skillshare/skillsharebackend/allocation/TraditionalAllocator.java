package com.skillshare.skillsharebackend.allocation;

import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.domain.WorkerService;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import com.skillshare.skillsharebackend.domain.enums.BookingStatus;
import com.skillshare.skillsharebackend.repository.BookingRepository;
import com.skillshare.skillsharebackend.repository.BookingSkillRepository;
import com.skillshare.skillsharebackend.repository.WorkerAvailabilityRepository;
import com.skillshare.skillsharebackend.repository.WorkerServiceRepository;
import com.skillshare.skillsharebackend.repository.WorkerSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The "traditional system" baseline allocator: skill filter ->
 * availability filter -> nearest worker, entirely in Java, entirely
 * ordinary JPA queries. Deliberately dumb by comparison to the
 * intelligent allocator (fn_find_candidates/fn_score_candidate/
 * sp_allocate_worker in PL/pgSQL, wired up as a second
 * {@link AllocationStrategy} in Phase 4):
 *
 * <ul>
 *   <li>Skill match here is a HARD filter (must have every required
 *       skill) — the intelligent allocator treats it as a soft scoring
 *       factor, so a worker missing one skill can still win on the
 *       strength of other factors. That difference is itself one of the
 *       things the research write-up compares.</li>
 *   <li>No rating, workload, or price weighting at all — first
 *       geographically-nearest candidate that clears the hard filters
 *       wins, full stop.</li>
 *   <li>No PostGIS — plain Haversine distance in Java (see
 *       {@link GeoUtils}), since the point of this class existing is to
 *       be an unremarkable application-layer implementation.</li>
 * </ul>
 *
 * Registered under the bean name "traditionalAllocator" so a later phase
 * can wire up both strategies side by side (e.g. an admin toggle, or the
 * Phase 9 comparison harness) without an ambiguous-bean error.
 */
@Service("traditionalAllocator")
@RequiredArgsConstructor
@Slf4j
public class TraditionalAllocator implements AllocationStrategy {

    /** Matches the Asia/Kolkata assumption already baked into
     *  fn_find_candidates (see 05_functions_procedures_v3.sql) — single-
     *  timezone marketplace, flagged there as a limitation if this ever
     *  goes multi-region. */
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");

    private final BookingSkillRepository bookingSkillRepository;
    private final WorkerServiceRepository workerServiceRepository;
    private final WorkerSkillRepository workerSkillRepository;
    private final WorkerAvailabilityRepository workerAvailabilityRepository;
    private final BookingRepository bookingRepository;

    @Override
    public Optional<Worker> selectWorker(Booking booking) {
        Long serviceId = booking.getService().getServiceId();

        Set<Long> requiredSkillIds = bookingSkillRepository
                .findByBooking_BookingId(booking.getBookingId()).stream()
                .map(bs -> bs.getSkill().getSkillId())
                .collect(Collectors.toSet());

        ZonedDateTime startLocal = booking.getScheduledStart().atZoneSameInstant(MARKET_ZONE);
        ZonedDateTime endLocal = booking.getScheduledEnd().atZoneSameInstant(MARKET_ZONE);
        // java.time.DayOfWeek: MONDAY=1 .. SUNDAY=7; Postgres EXTRACT(DOW): SUNDAY=0 .. SATURDAY=6.
        short dayOfWeek = (short) (startLocal.getDayOfWeek().getValue() % 7);
        LocalTime localStart = startLocal.toLocalTime();
        LocalTime localEnd = endLocal.toLocalTime();

        List<WorkerService> offerings = workerServiceRepository.findByService_ServiceId(serviceId);

        Worker best = null;
        double bestDistanceKm = Double.MAX_VALUE;

        for (WorkerService offering : offerings) {
            Worker candidate = offering.getWorker();

            if (candidate.getStatus() != AccountStatus.active || candidate.getLocation() == null) {
                continue;
            }

            if (!requiredSkillIds.isEmpty()) {
                Set<Long> candidateSkillIds = workerSkillRepository
                        .findByWorker_WorkerId(candidate.getWorkerId()).stream()
                        .map(ws -> ws.getSkill().getSkillId())
                        .collect(Collectors.toSet());
                if (!candidateSkillIds.containsAll(requiredSkillIds)) {
                    continue; // hard filter: unlike fn_score_candidate, a partial match is disqualified
                }
            }

            boolean available = workerAvailabilityRepository
                    .findByWorker_WorkerIdAndDayOfWeek(candidate.getWorkerId(), dayOfWeek).stream()
                    .anyMatch(slot -> !slot.getStartTime().isAfter(localStart)
                            && !slot.getEndTime().isBefore(localEnd));
            if (!available) {
                continue;
            }

            boolean alreadyBusy = !bookingRepository.findOverlapping(
                    candidate.getWorkerId(), booking.getBookingId(),
                    booking.getScheduledStart(), booking.getScheduledEnd(), BookingStatus.cancelled).isEmpty();
            if (alreadyBusy) {
                continue;
            }

            double distanceKm = GeoUtils.haversineKm(
                    candidate.getLocation().getLatitude(), candidate.getLocation().getLongitude(),
                    booking.getLocation().getLatitude(), booking.getLocation().getLongitude());

            if (distanceKm < bestDistanceKm) {
                bestDistanceKm = distanceKm;
                best = candidate;
            }
        }

        if (best == null) {
            log.info("TraditionalAllocator: no eligible worker found for booking {}", booking.getBookingId());
        } else {
            log.info("TraditionalAllocator: booking {} -> worker {} ({} km)",
                    booking.getBookingId(), best.getWorkerId(), String.format("%.2f", bestDistanceKm));
        }

        return Optional.ofNullable(best);
    }
}
