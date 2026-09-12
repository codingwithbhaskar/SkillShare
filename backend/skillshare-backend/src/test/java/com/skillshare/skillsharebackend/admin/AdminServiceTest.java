package com.skillshare.skillsharebackend.admin;

import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.Service;
import com.skillshare.skillsharebackend.domain.Skill;
import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.domain.WorkerStats;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import com.skillshare.skillsharebackend.domain.enums.BookingStatus;
import com.skillshare.skillsharebackend.domain.enums.PaymentStatus;
import com.skillshare.skillsharebackend.domain.enums.UserRole;
import com.skillshare.skillsharebackend.repository.BookingRepository;
import com.skillshare.skillsharebackend.repository.BookingSkillRepository;
import com.skillshare.skillsharebackend.repository.PaymentRepository;
import com.skillshare.skillsharebackend.repository.ServiceRepository;
import com.skillshare.skillsharebackend.repository.SkillRepository;
import com.skillshare.skillsharebackend.repository.UserRepository;
import com.skillshare.skillsharebackend.repository.WorkerRepository;
import com.skillshare.skillsharebackend.repository.WorkerServiceRepository;
import com.skillshare.skillsharebackend.repository.WorkerSkillRepository;
import com.skillshare.skillsharebackend.repository.WorkerStatsRepository;
import com.skillshare.skillsharebackend.web.dto.AdminReportResponse;
import com.skillshare.skillsharebackend.web.dto.AdminStatsResponse;
import com.skillshare.skillsharebackend.web.dto.AdminUserResponse;
import com.skillshare.skillsharebackend.web.dto.ServiceRequest;
import com.skillshare.skillsharebackend.web.dto.ServiceResponse;
import com.skillshare.skillsharebackend.web.dto.SkillRequest;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminService}. All repositories are mocked, same
 * style as {@code WorkerDashboardServiceTest}/{@code BookingServiceTest}
 * - these pin down the aggregation/validation/delete-guard logic, not a
 * real Postgres round-trip (the live verification bar every phase since
 * Phase 1 has cleared).
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ServiceRepository serviceRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private WorkerServiceRepository workerServiceRepository;
    @Mock
    private WorkerSkillRepository workerSkillRepository;
    @Mock
    private BookingSkillRepository bookingSkillRepository;
    @Mock
    private WorkerStatsRepository workerStatsRepository;
    @Mock
    private WorkerRepository workerRepository;

    private AdminService service;

    @BeforeEach
    void setUp() {
        service = new AdminService(userRepository, serviceRepository, skillRepository, bookingRepository,
                paymentRepository, workerServiceRepository, workerSkillRepository, bookingSkillRepository,
                workerStatsRepository, workerRepository);
    }

    // ---------------------------------------------------------------
    // Stats
    // ---------------------------------------------------------------

    @Test
    void getStats_aggregatesAcrossRepositories() {
        when(userRepository.count()).thenReturn(10L);
        when(userRepository.countByRole(UserRole.customer)).thenReturn(6L);
        when(userRepository.countByRole(UserRole.worker)).thenReturn(3L);
        when(userRepository.countByRole(UserRole.admin)).thenReturn(1L);
        when(userRepository.countByStatus(AccountStatus.active)).thenReturn(9L);
        when(userRepository.countByStatus(AccountStatus.suspended)).thenReturn(1L);
        when(serviceRepository.count()).thenReturn(5L);
        when(skillRepository.count()).thenReturn(8L);
        when(bookingRepository.count()).thenReturn(20L);
        for (BookingStatus status : BookingStatus.values()) {
            when(bookingRepository.countByStatus(status)).thenReturn(2L);
        }
        when(bookingRepository.findTop10ByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(paymentRepository.sumByStatus(PaymentStatus.completed)).thenReturn(new BigDecimal("1500.00"));

        AdminStatsResponse stats = service.getStats();

        assertEquals(10L, stats.totalUsers());
        assertEquals(6L, stats.totalCustomers());
        assertEquals(3L, stats.totalWorkers());
        assertEquals(1L, stats.totalAdmins());
        assertEquals(9L, stats.activeUsers());
        assertEquals(1L, stats.suspendedUsers());
        assertEquals(5L, stats.totalServices());
        assertEquals(8L, stats.totalSkills());
        assertEquals(20L, stats.totalBookings());
        assertEquals(new BigDecimal("1500.00"), stats.totalRevenue());
        assertEquals(BookingStatus.values().length, stats.bookingsByStatus().size());
    }

    // ---------------------------------------------------------------
    // Users
    // ---------------------------------------------------------------

    @Test
    void listUsers_blankSearchIsNormalizedToNull() {
        when(userRepository.search(UserRole.worker, null, null)).thenReturn(List.of());

        service.listUsers(UserRole.worker, null, "   ");

        verify(userRepository).search(UserRole.worker, null, null);
    }

    @Test
    void updateUserStatus_throwsNotFound_whenUserMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AdminNotFoundException.class, () -> service.updateUserStatus(99L, AccountStatus.suspended));
    }

    @Test
    void updateUserStatus_throwsValidation_whenTargetIsAdmin() {
        User admin = User.builder().userId(1L).role(UserRole.admin).status(AccountStatus.active).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThrows(AdminValidationException.class, () -> service.updateUserStatus(1L, AccountStatus.suspended));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserStatus_succeeds_forNonAdmin() {
        User customer = User.builder().userId(2L).role(UserRole.customer).fullName("A").email("a@x.com")
                .status(AccountStatus.active).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(customer));

        AdminUserResponse response = service.updateUserStatus(2L, AccountStatus.suspended);

        assertEquals(AccountStatus.suspended, response.status());
        verify(userRepository).save(customer);
        // Not a worker - the workers.status sync below shouldn't be touched.
        verifyNoInteractions(workerRepository);
    }

    @Test
    void updateUserStatus_syncsWorkerProfileStatus_whenTargetIsWorker() {
        User workerUser = User.builder().userId(2L).role(UserRole.worker).fullName("W").email("w@x.com")
                .status(AccountStatus.active).build();
        Worker workerProfile = Worker.builder().workerId(9L).user(workerUser).status(AccountStatus.active).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(workerUser));
        when(workerRepository.findByUser_UserId(2L)).thenReturn(Optional.of(workerProfile));

        service.updateUserStatus(2L, AccountStatus.suspended);

        // This is the fix that closes the "suspended/deactivated worker
        // still gets offered to customers" gap - every allocation path
        // (fn_find_candidates, TraditionalAllocator, the nearby-workers
        // native query) filters on workers.status, not users.status.
        assertEquals(AccountStatus.suspended, workerProfile.getStatus());
        verify(workerRepository).save(workerProfile);
    }

    @Test
    void updateUserStatus_doesNotFail_whenWorkerRoleUserHasNoWorkerRow() {
        // Defensive: shouldn't happen in practice (AuthService.register
        // always creates the companion row for role = worker), but the
        // sync must not NPE if it's ever missing.
        User workerUser = User.builder().userId(2L).role(UserRole.worker).fullName("W").email("w@x.com")
                .status(AccountStatus.active).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(workerUser));
        when(workerRepository.findByUser_UserId(2L)).thenReturn(Optional.empty());

        AdminUserResponse response = service.updateUserStatus(2L, AccountStatus.suspended);

        assertEquals(AccountStatus.suspended, response.status());
        verify(workerRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Services
    // ---------------------------------------------------------------

    @Test
    void createService_throwsValidation_onBlankName() {
        assertThrows(AdminValidationException.class,
                () -> service.createService(new ServiceRequest("  ", null, null)));
    }

    @Test
    void createService_throwsValidation_onDuplicateName() {
        when(serviceRepository.findByServiceName("Plumbing"))
                .thenReturn(Optional.of(Service.builder().serviceId(1L).serviceName("Plumbing").build()));

        assertThrows(AdminValidationException.class,
                () -> service.createService(new ServiceRequest("Plumbing", "Home", null)));
    }

    @Test
    void createService_succeeds_whenNameIsFree() {
        when(serviceRepository.findByServiceName("Plumbing")).thenReturn(Optional.empty());
        when(serviceRepository.save(any())).thenAnswer(inv -> {
            Service s = inv.getArgument(0);
            s.setServiceId(7L);
            return s;
        });

        ServiceResponse response = service.createService(new ServiceRequest("Plumbing", "Home", "Pipes"));

        assertEquals(7L, response.serviceId());
        assertEquals("Plumbing", response.serviceName());
    }

    @Test
    void deleteService_throwsNotFound_whenMissing() {
        when(serviceRepository.existsById(1L)).thenReturn(false);

        assertThrows(AdminNotFoundException.class, () -> service.deleteService(1L));
    }

    @Test
    void deleteService_throwsValidation_whenReferencedByBookings() {
        when(serviceRepository.existsById(1L)).thenReturn(true);
        when(bookingRepository.existsByService_ServiceId(1L)).thenReturn(true);

        assertThrows(AdminValidationException.class, () -> service.deleteService(1L));
        verify(serviceRepository, never()).deleteById(any());
    }

    @Test
    void deleteService_throwsValidation_whenOfferedByWorker() {
        when(serviceRepository.existsById(1L)).thenReturn(true);
        when(bookingRepository.existsByService_ServiceId(1L)).thenReturn(false);
        when(workerServiceRepository.existsByService_ServiceId(1L)).thenReturn(true);

        assertThrows(AdminValidationException.class, () -> service.deleteService(1L));
        verify(serviceRepository, never()).deleteById(any());
    }

    @Test
    void deleteService_succeeds_whenUnreferenced() {
        when(serviceRepository.existsById(1L)).thenReturn(true);
        when(bookingRepository.existsByService_ServiceId(1L)).thenReturn(false);
        when(workerServiceRepository.existsByService_ServiceId(1L)).thenReturn(false);

        service.deleteService(1L);

        verify(serviceRepository, times(1)).deleteById(1L);
    }

    // ---------------------------------------------------------------
    // Skills
    // ---------------------------------------------------------------

    @Test
    void deleteSkill_throwsValidation_whenRequiredByBookingHistory() {
        when(skillRepository.existsById(3L)).thenReturn(true);
        when(bookingSkillRepository.existsBySkill_SkillId(3L)).thenReturn(true);

        assertThrows(AdminValidationException.class, () -> service.deleteSkill(3L));
        verify(skillRepository, never()).deleteById(any());
    }

    @Test
    void createSkill_throwsValidation_onDuplicateName() {
        when(skillRepository.findBySkillName("Wiring"))
                .thenReturn(Optional.of(Skill.builder().skillId(1L).skillName("Wiring").build()));

        assertThrows(AdminValidationException.class, () -> service.createSkill(new SkillRequest("Wiring", null, null)));
    }

    // ---------------------------------------------------------------
    // Reports
    // ---------------------------------------------------------------

    @Test
    void getReport_throwsValidation_whenFromIsNotBeforeTo() {
        OffsetDateTime now = OffsetDateTime.now();

        assertThrows(AdminValidationException.class, () -> service.getReport(now, now.minusDays(1)));
    }

    @Test
    void getReport_aggregatesTopWorkersAndTopServices() {
        OffsetDateTime from = OffsetDateTime.now().minusDays(7);
        OffsetDateTime to = OffsetDateTime.now();

        User workerUser = User.builder().userId(50L).fullName("Priya").build();
        Worker worker = Worker.builder().workerId(5L).user(workerUser).build();
        Service plumbing = Service.builder().serviceId(1L).serviceName("Plumbing").build();

        Booking completed1 = Booking.builder().bookingId(1L).status(BookingStatus.completed)
                .worker(worker).service(plumbing).totalAmount(new BigDecimal("500.00")).build();
        Booking completed2 = Booking.builder().bookingId(2L).status(BookingStatus.completed)
                .worker(worker).service(plumbing).totalAmount(new BigDecimal("300.00")).build();
        Booking cancelled = Booking.builder().bookingId(3L).status(BookingStatus.cancelled)
                .service(plumbing).build();

        when(bookingRepository.findByScheduledStartBetween(from, to))
                .thenReturn(List.of(completed1, completed2, cancelled));
        when(workerStatsRepository.findById(5L)).thenReturn(Optional.of(
                statsWithRating(5L, new BigDecimal("4.5"))));
        when(paymentRepository.sumByStatusBetween(PaymentStatus.completed, from, to)).thenReturn(new BigDecimal("800.00"));

        AdminReportResponse report = service.getReport(from, to);

        assertEquals(3, report.totalBookings());
        assertEquals(2, report.completedBookings());
        assertEquals(1, report.cancelledBookings());
        assertEquals(new BigDecimal("800.00"), report.totalRevenue());
        assertEquals(1, report.topWorkers().size());
        assertEquals(5L, report.topWorkers().get(0).workerId());
        assertEquals(2L, report.topWorkers().get(0).completedBookings());
        assertEquals(new BigDecimal("4.5"), report.topWorkers().get(0).avgRating());
        assertEquals(1, report.topServices().size());
        assertEquals("Plumbing", report.topServices().get(0).serviceName());
        assertEquals(2L, report.topServices().get(0).bookingCount());
        assertEquals(new BigDecimal("800.00"), report.topServices().get(0).revenue());
    }

    private WorkerStats statsWithRating(Long workerId, BigDecimal avgRating) {
        WorkerStats stats = new WorkerStats();
        stats.setWorkerId(workerId);
        stats.setAvgRating(avgRating);
        return stats;
    }
}
