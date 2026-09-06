package com.skillshare.skillsharebackend.dashboard;

import com.skillshare.skillsharebackend.allocation.WorkerNotFoundException;
import com.skillshare.skillsharebackend.domain.Location;
import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.domain.WorkerAvailability;
import com.skillshare.skillsharebackend.domain.WorkerSkill;
import com.skillshare.skillsharebackend.domain.WorkerService;
import com.skillshare.skillsharebackend.domain.enums.ProficiencyLevel;
import com.skillshare.skillsharebackend.domain.ids.WorkerServiceId;
import com.skillshare.skillsharebackend.domain.ids.WorkerSkillId;
import com.skillshare.skillsharebackend.repository.BookingRepository;
import com.skillshare.skillsharebackend.repository.LocationRepository;
import com.skillshare.skillsharebackend.repository.ReviewRepository;
import com.skillshare.skillsharebackend.repository.ServiceRepository;
import com.skillshare.skillsharebackend.repository.SkillRepository;
import com.skillshare.skillsharebackend.repository.WorkerAvailabilityRepository;
import com.skillshare.skillsharebackend.repository.WorkerRepository;
import com.skillshare.skillsharebackend.repository.WorkerServiceRepository;
import com.skillshare.skillsharebackend.repository.WorkerSkillRepository;
import com.skillshare.skillsharebackend.repository.WorkerStatsRepository;
import com.skillshare.skillsharebackend.security.AuthenticatedUser;
import com.skillshare.skillsharebackend.security.ForbiddenException;
import com.skillshare.skillsharebackend.web.dto.BookingSummaryResponse;
import com.skillshare.skillsharebackend.web.dto.ReviewResponse;
import com.skillshare.skillsharebackend.web.dto.UpdateWorkerProfileRequest;
import com.skillshare.skillsharebackend.web.dto.WorkerProfileResponse;
import com.skillshare.skillsharebackend.web.dto.WorkerStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Phase 5 - worker dashboard. Thin wrapping of repository methods that
 * already existed before this phase ({@code findByWorker_WorkerId} on
 * both BookingRepository and ReviewRepository, WorkerStatsRepository's
 * inherited {@code findById} against {@code mv_worker_stats}) - see
 * dev-status-and-next-steps.md's Phase 5 plan. All three methods verify
 * the worker id is real first, reusing {@link WorkerNotFoundException}
 * from the allocation package (already mapped to 404 by
 * GlobalExceptionHandler) rather than defining a second copy of the same
 * exception.
 *
 * <p>{@code SecurityConfig}'s {@code /api/workers/**} rule already
 * requires WORKER or ADMIN, but that alone lets any worker view any
 * OTHER worker's dashboard just by guessing a {@code workerId} in the
 * URL - the {@link AuthenticatedUser}-aware overloads below (used by
 * {@code WorkerDashboardController}) close that by requiring a WORKER
 * caller's own {@code workerId} to match, while an ADMIN caller can still
 * view any worker's dashboard.
 *
 * <p>{@link #updateMyProfile} is the Phase 8 follow-up "complete my
 * profile" endpoint - see {@link UpdateWorkerProfileRequest}'s javadoc
 * for why this is more than a cosmetic profile-editing nicety: without
 * it, a registered worker has no location, no service offerings, and no
 * availability, all three of which {@code fn_find_candidates} hard-
 * requires, so they could never be allocated a single booking.
 */
@Service
@RequiredArgsConstructor
public class WorkerDashboardService {

    private final WorkerRepository workerRepository;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final WorkerStatsRepository workerStatsRepository;
    private final LocationRepository locationRepository;
    private final SkillRepository skillRepository;
    private final ServiceRepository serviceRepository;
    private final WorkerSkillRepository workerSkillRepository;
    private final WorkerServiceRepository workerServiceRepository;
    private final WorkerAvailabilityRepository workerAvailabilityRepository;

    /** Looked up by {@code user_id} (the JWT subject), not {@code
     *  worker_id} - this is the one lookup direction every other method
     *  in this class doesn't need, since the frontend doesn't know its
     *  own workerId until this call returns it. */
    @Transactional(readOnly = true)
    public WorkerProfileResponse getMyProfile(Long userId) {
        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new WorkerNotFoundException(userId));
        return buildFullProfile(worker);
    }

    @Transactional
    public WorkerProfileResponse updateMyProfile(Long userId, UpdateWorkerProfileRequest request) {
        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new WorkerNotFoundException(userId));

        if (request.bio() != null) {
            worker.setBio(request.bio());
        }
        if (request.experienceYears() != null) {
            worker.setExperienceYears(request.experienceYears());
        }
        if (request.baseHourlyRate() != null) {
            worker.setBaseHourlyRate(request.baseHourlyRate());
        }

        // A fresh Location row per update, same pattern
        // BookingService#createBooking uses for job addresses - see
        // UpdateWorkerProfileRequest's javadoc for why this isn't a
        // mutate-in-place of any existing worker.location row.
        if (request.addressLine() != null) {
            if (request.city() == null || request.state() == null
                    || request.latitude() == null || request.longitude() == null) {
                throw new WorkerProfileValidationException(
                        "addressLine, city, state, latitude, and longitude are all required to set a location");
            }
            Location location = locationRepository.save(Location.builder()
                    .addressLine(request.addressLine())
                    .landmark(request.landmark())
                    .city(request.city())
                    .state(request.state())
                    .pincode(request.pincode())
                    .latitude(request.latitude())
                    .longitude(request.longitude())
                    .build());
            worker.setLocation(location);
        }

        workerRepository.save(worker);

        if (request.skillIds() != null) {
            replaceSkills(worker, request.skillIds());
        }
        if (request.serviceRates() != null) {
            replaceServiceRates(worker, request.serviceRates());
        }
        if (request.availability() != null) {
            replaceAvailability(worker, request.availability());
        }

        return buildFullProfile(worker);
    }

    /** Assembles the enriched {@link WorkerProfileResponse} - see that
     *  record's javadoc for why the extra queries here are worth it (the
     *  profile-edit form needs to show what's already on file, not just
     *  a blank slate every time). */
    private WorkerProfileResponse buildFullProfile(Worker worker) {
        List<Long> skillIds = workerSkillRepository.findByWorker_WorkerId(worker.getWorkerId()).stream()
                .map(ws -> ws.getSkill().getSkillId())
                .toList();
        List<WorkerProfileResponse.ServiceRateView> serviceRates =
                workerServiceRepository.findByWorker_WorkerId(worker.getWorkerId()).stream()
                        .map(ws -> new WorkerProfileResponse.ServiceRateView(
                                ws.getService().getServiceId(), ws.getHourlyRate()))
                        .toList();
        List<WorkerProfileResponse.AvailabilityWindowView> availability =
                workerAvailabilityRepository.findByWorker_WorkerId(worker.getWorkerId()).stream()
                        .map(a -> new WorkerProfileResponse.AvailabilityWindowView(
                                a.getDayOfWeek(), a.getStartTime(), a.getEndTime()))
                        .toList();
        return WorkerProfileResponse.from(worker, skillIds, serviceRates, availability);
    }

    private void replaceSkills(Worker worker, List<Long> skillIds) {
        workerSkillRepository.deleteAll(workerSkillRepository.findByWorker_WorkerId(worker.getWorkerId()));
        for (Long skillId : skillIds) {
            var skill = skillRepository.findById(skillId)
                    .orElseThrow(() -> new WorkerProfileValidationException("No skill found with id " + skillId));
            workerSkillRepository.save(WorkerSkill.builder()
                    .id(new WorkerSkillId(worker.getWorkerId(), skillId))
                    .worker(worker)
                    .skill(skill)
                    // No per-skill proficiency input in the profile form
                    // yet (out of scope - fn_score_candidate never reads
                    // this column, only worker_skills' existence for the
                    // soft skill-match score) - intermediate is a
                    // reasonable, honest default rather than a guess at
                    // either extreme.
                    .proficiencyLevel(ProficiencyLevel.intermediate)
                    .build());
        }
    }

    private void replaceServiceRates(Worker worker, List<UpdateWorkerProfileRequest.ServiceRate> serviceRates) {
        workerServiceRepository.deleteAll(workerServiceRepository.findByWorker_WorkerId(worker.getWorkerId()));
        for (UpdateWorkerProfileRequest.ServiceRate rate : serviceRates) {
            if (rate.hourlyRate() == null || rate.hourlyRate().signum() <= 0) {
                throw new WorkerProfileValidationException(
                        "hourlyRate for service " + rate.serviceId() + " must be a positive amount");
            }
            var service = serviceRepository.findById(rate.serviceId())
                    .orElseThrow(() -> new WorkerProfileValidationException(
                            "No service found with id " + rate.serviceId()));
            workerServiceRepository.save(WorkerService.builder()
                    .id(new WorkerServiceId(worker.getWorkerId(), rate.serviceId()))
                    .worker(worker)
                    .service(service)
                    .hourlyRate(rate.hourlyRate())
                    .build());
        }
    }

    private void replaceAvailability(Worker worker, List<UpdateWorkerProfileRequest.AvailabilityWindow> windows) {
        workerAvailabilityRepository.deleteAll(
                workerAvailabilityRepository.findByWorker_WorkerId(worker.getWorkerId()));
        for (UpdateWorkerProfileRequest.AvailabilityWindow window : windows) {
            if (window.dayOfWeek() == null || window.dayOfWeek() < 0 || window.dayOfWeek() > 6) {
                throw new WorkerProfileValidationException("dayOfWeek must be between 0 (Sunday) and 6 (Saturday)");
            }
            if (window.startTime() == null || window.endTime() == null || !window.endTime().isAfter(window.startTime())) {
                throw new WorkerProfileValidationException("Each availability window needs endTime after startTime");
            }
            workerAvailabilityRepository.save(WorkerAvailability.builder()
                    .worker(worker)
                    .dayOfWeek(window.dayOfWeek())
                    .startTime(window.startTime())
                    .endTime(window.endTime())
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public List<BookingSummaryResponse> getBookings(Long workerId) {
        requireWorker(workerId);
        return bookingRepository.findByWorker_WorkerId(workerId).stream()
                .map(BookingSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingSummaryResponse> getBookings(Long workerId, AuthenticatedUser caller) {
        requireSelfOrAdmin(workerId, caller);
        return getBookings(workerId);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviews(Long workerId) {
        requireWorker(workerId);
        return reviewRepository.findByWorker_WorkerId(workerId).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviews(Long workerId, AuthenticatedUser caller) {
        requireSelfOrAdmin(workerId, caller);
        return getReviews(workerId);
    }

    @Transactional(readOnly = true)
    public WorkerStatsResponse getStats(Long workerId) {
        requireWorker(workerId);
        return workerStatsRepository.findById(workerId)
                .map(WorkerStatsResponse::from)
                .orElseGet(() -> WorkerStatsResponse.zero(workerId));
    }

    @Transactional(readOnly = true)
    public WorkerStatsResponse getStats(Long workerId, AuthenticatedUser caller) {
        requireSelfOrAdmin(workerId, caller);
        return getStats(workerId);
    }

    private void requireWorker(Long workerId) {
        if (!workerRepository.existsById(workerId)) {
            throw new WorkerNotFoundException(workerId);
        }
    }

    private void requireSelfOrAdmin(Long workerId, AuthenticatedUser caller) {
        if (caller.isAdmin()) {
            return;
        }
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new WorkerNotFoundException(workerId));
        if (!worker.getUser().getUserId().equals(caller.userId())) {
            throw new ForbiddenException("You do not have access to worker " + workerId + "'s dashboard");
        }
    }
}
