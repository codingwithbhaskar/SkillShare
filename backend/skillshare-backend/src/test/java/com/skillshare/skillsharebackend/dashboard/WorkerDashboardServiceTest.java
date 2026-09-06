package com.skillshare.skillsharebackend.dashboard;

import com.skillshare.skillsharebackend.allocation.WorkerNotFoundException;
import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.domain.Worker;
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
import com.skillshare.skillsharebackend.domain.Skill;
import com.skillshare.skillsharebackend.security.AuthenticatedUser;
import com.skillshare.skillsharebackend.security.ForbiddenException;
import com.skillshare.skillsharebackend.web.dto.UpdateWorkerProfileRequest;
import com.skillshare.skillsharebackend.web.dto.WorkerProfileResponse;
import com.skillshare.skillsharebackend.web.dto.WorkerStatsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@link AuthenticatedUser}-aware overloads added to
 * close the "any worker can view any other worker's dashboard by
 * guessing a workerId" gap (see this class's own javadoc). The plain,
 * caller-less methods were already implicitly covered by live testing
 * (dev-status-and-next-steps.md's Phase 5 section); these pin down the
 * new authorization logic specifically.
 */
@ExtendWith(MockitoExtension.class)
class WorkerDashboardServiceTest {

    @Mock
    private WorkerRepository workerRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private WorkerStatsRepository workerStatsRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private ServiceRepository serviceRepository;
    @Mock
    private WorkerSkillRepository workerSkillRepository;
    @Mock
    private WorkerServiceRepository workerServiceRepository;
    @Mock
    private WorkerAvailabilityRepository workerAvailabilityRepository;

    private WorkerDashboardService service;

    @BeforeEach
    void setUp() {
        service = new WorkerDashboardService(workerRepository, bookingRepository, reviewRepository,
                workerStatsRepository, locationRepository, skillRepository, serviceRepository,
                workerSkillRepository, workerServiceRepository, workerAvailabilityRepository);
    }

    private Worker workerOwnedBy(Long userId) {
        return Worker.builder().workerId(2L).user(User.builder().userId(userId).build()).build();
    }

    @Test
    void getStats_withCaller_throwsForbidden_whenWorkerCallerIsNotTheOwner() {
        when(workerRepository.findById(2L)).thenReturn(Optional.of(workerOwnedBy(1L)));
        AuthenticatedUser stranger = new AuthenticatedUser(999L, "worker");

        assertThrows(ForbiddenException.class, () -> service.getStats(2L, stranger));
    }

    @Test
    void getStats_withCaller_succeeds_forOwningWorker() {
        when(workerRepository.findById(2L)).thenReturn(Optional.of(workerOwnedBy(1L)));
        when(workerRepository.existsById(2L)).thenReturn(true);
        when(workerStatsRepository.findById(2L)).thenReturn(Optional.empty());
        AuthenticatedUser owner = new AuthenticatedUser(1L, "worker");

        WorkerStatsResponse response = service.getStats(2L, owner);

        assertEquals(2L, response.workerId());
    }

    @Test
    void getStats_withCaller_succeeds_forAdmin_regardlessOfOwner() {
        when(workerRepository.existsById(2L)).thenReturn(true);
        when(workerStatsRepository.findById(2L)).thenReturn(Optional.empty());
        AuthenticatedUser admin = new AuthenticatedUser(42L, "admin");

        WorkerStatsResponse response = service.getStats(2L, admin);

        assertEquals(2L, response.workerId());
    }

    @Test
    void getStats_withCaller_throwsWorkerNotFound_whenWorkerDoesNotExist() {
        when(workerRepository.findById(2L)).thenReturn(Optional.empty());
        AuthenticatedUser someWorker = new AuthenticatedUser(1L, "worker");

        assertThrows(WorkerNotFoundException.class, () -> service.getStats(2L, someWorker));
    }

    @Test
    void updateMyProfile_updatesBioOnly_whenOnlyBioProvided() {
        Worker worker = Worker.builder().workerId(2L).user(User.builder().userId(1L).build()).build();
        when(workerRepository.findByUser_UserId(1L)).thenReturn(Optional.of(worker));

        UpdateWorkerProfileRequest request = new UpdateWorkerProfileRequest(
                "Experienced electrician", null, null, null, null, null, null, null, null, null, null, null, null);

        WorkerProfileResponse response = service.updateMyProfile(1L, request);

        assertEquals("Experienced electrician", worker.getBio());
        assertEquals(2L, response.workerId());
    }

    @Test
    void updateMyProfile_throwsValidation_whenAddressLineProvidedWithoutCity() {
        Worker worker = Worker.builder().workerId(2L).user(User.builder().userId(1L).build()).build();
        when(workerRepository.findByUser_UserId(1L)).thenReturn(Optional.of(worker));

        UpdateWorkerProfileRequest request = new UpdateWorkerProfileRequest(
                null, null, null, "123 Main St", null, null, null, null, null, null, null, null, null);

        assertThrows(WorkerProfileValidationException.class, () -> service.updateMyProfile(1L, request));
    }

    @Test
    void updateMyProfile_replacesSkills_whenSkillIdsProvided() {
        Worker worker = Worker.builder().workerId(2L).user(User.builder().userId(1L).build()).build();
        when(workerRepository.findByUser_UserId(1L)).thenReturn(Optional.of(worker));
        when(workerSkillRepository.findByWorker_WorkerId(2L)).thenReturn(java.util.List.of());
        when(skillRepository.findById(9L)).thenReturn(Optional.of(Skill.builder().skillId(9L).build()));

        UpdateWorkerProfileRequest request = new UpdateWorkerProfileRequest(
                null, null, null, null, null, null, null, null, null, null, java.util.List.of(9L), null, null);

        service.updateMyProfile(1L, request);

        verify(workerSkillRepository).save(org.mockito.ArgumentMatchers.any());
    }
}
