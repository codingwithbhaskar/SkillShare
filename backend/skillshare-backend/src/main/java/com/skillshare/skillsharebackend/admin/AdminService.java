package com.skillshare.skillsharebackend.admin;

import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.Skill;
import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.domain.Worker;
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
import com.skillshare.skillsharebackend.web.dto.AdminBookingResponse;
import com.skillshare.skillsharebackend.web.dto.AdminReportResponse;
import com.skillshare.skillsharebackend.web.dto.AdminStatsResponse;
import com.skillshare.skillsharebackend.web.dto.AdminUserResponse;
import com.skillshare.skillsharebackend.web.dto.ServiceRequest;
import com.skillshare.skillsharebackend.web.dto.ServiceResponse;
import com.skillshare.skillsharebackend.web.dto.SkillRequest;
import com.skillshare.skillsharebackend.web.dto.SkillResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Backend for the admin panel - the "no admin-specific backend endpoints
 * exist yet" gap {@code AdminPage.jsx}'s Phase 8 placeholder documented
 * explicitly (see phase-8-frontend-in-progress.md). Everything here is
 * reached only via {@code AdminController}, itself gated to ROLE_ADMIN
 * at the path level by {@code SecurityConfig} (same coarse
 * path-based-role pattern as {@code /api/workers/**} - there's no
 * resource-instance ownership dimension to an admin action the way
 * there is for a booking or a worker dashboard, so no
 * {@code AuthenticatedUser}-aware overloads are needed here).
 *
 * <p>Services and skills have no soft-delete/active flag in the schema
 * (01_schema_v3.sql) - {@link #deleteService} and {@link #deleteSkill}
 * are hard deletes, guarded by an explicit reference check first so an
 * admin gets a clear {@link AdminValidationException} (400) instead of a
 * raw FK-violation error from the database.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final SkillRepository skillRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final WorkerServiceRepository workerServiceRepository;
    private final WorkerSkillRepository workerSkillRepository;
    private final BookingSkillRepository bookingSkillRepository;
    private final WorkerStatsRepository workerStatsRepository;
    private final WorkerRepository workerRepository;

    // ---------------------------------------------------------------
    // Dashboard
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        Map<BookingStatus, Long> byStatus = new EnumMap<>(BookingStatus.class);
        for (BookingStatus status : BookingStatus.values()) {
            byStatus.put(status, bookingRepository.countByStatus(status));
        }
        List<AdminBookingResponse> recent = bookingRepository.findTop10ByOrderByCreatedAtDesc().stream()
                .map(AdminBookingResponse::from)
                .toList();
        return new AdminStatsResponse(
                userRepository.count(),
                userRepository.countByRole(UserRole.customer),
                userRepository.countByRole(UserRole.worker),
                userRepository.countByRole(UserRole.admin),
                userRepository.countByStatus(AccountStatus.active),
                userRepository.countByStatus(AccountStatus.suspended),
                serviceRepository.count(),
                skillRepository.count(),
                bookingRepository.count(),
                byStatus,
                paymentRepository.sumByStatus(PaymentStatus.completed),
                recent);
    }

    // ---------------------------------------------------------------
    // Users
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers(UserRole role, AccountStatus status, String search) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        return userRepository.search(role, status, normalizedSearch).stream()
                .map(AdminUserResponse::from)
                .toList();
    }

    @Transactional
    public AdminUserResponse updateUserStatus(Long userId, AccountStatus status) {
        if (status == null) {
            throw new AdminValidationException("status is required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AdminNotFoundException("No user found with id " + userId));
        if (user.getRole() == UserRole.admin && status != AccountStatus.active) {
            // An admin account is the only thing standing between the
            // platform and nobody being able to manage it - blocking
            // this here is a deliberate guardrail, not an oversight to
            // fix later.
            throw new AdminValidationException("An admin account cannot be deactivated or suspended");
        }
        user.setStatus(status);
        userRepository.save(user);
        // Keep the worker profile's own status column in sync. It's a
        // separate column from users.status (see workers.status's own
        // comment in V1__schema.sql), and it's the ONLY thing every
        // allocation path actually filters on - fn_find_candidates'
        // `WHERE w.status = 'active'` (the intelligent PL/pgSQL
        // allocator), TraditionalAllocator's own check, and
        // WorkerRepository.findWorkersWithinRadius' native query.
        // Without this sync, suspending or deactivating a worker's user
        // account would leave their worker profile status = 'active' and
        // they'd keep being offered to customers as if nothing changed.
        if (user.getRole() == UserRole.worker) {
            workerRepository.findByUser_UserId(userId).ifPresent(worker -> {
                worker.setStatus(status);
                workerRepository.save(worker);
            });
        }
        return AdminUserResponse.from(user);
    }

    // ---------------------------------------------------------------
    // Bookings (read-only overview)
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AdminBookingResponse> listBookings(BookingStatus status) {
        List<Booking> bookings = status != null ? bookingRepository.findByStatus(status) : bookingRepository.findAll();
        return bookings.stream()
                .sorted(Comparator.comparing(Booking::getCreatedAt).reversed())
                .map(AdminBookingResponse::from)
                .toList();
    }

    // ---------------------------------------------------------------
    // Services
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ServiceResponse> listServices() {
        return serviceRepository.findAll().stream().map(ServiceResponse::from).toList();
    }

    @Transactional
    public ServiceResponse createService(ServiceRequest request) {
        String name = requireName(request.serviceName(), "serviceName");
        if (serviceRepository.findByServiceName(name).isPresent()) {
            throw new AdminValidationException("A service named \"" + name + "\" already exists");
        }
        com.skillshare.skillsharebackend.domain.Service service = serviceRepository.save(
                com.skillshare.skillsharebackend.domain.Service.builder()
                        .serviceName(name)
                        .category(blankToNull(request.category()))
                        .description(blankToNull(request.description()))
                        .build());
        return ServiceResponse.from(service);
    }

    @Transactional
    public ServiceResponse updateService(Long serviceId, ServiceRequest request) {
        com.skillshare.skillsharebackend.domain.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new AdminNotFoundException("No service found with id " + serviceId));
        String name = requireName(request.serviceName(), "serviceName");
        if (serviceRepository.findByServiceNameAndServiceIdNot(name, serviceId).isPresent()) {
            throw new AdminValidationException("A service named \"" + name + "\" already exists");
        }
        service.setServiceName(name);
        service.setCategory(blankToNull(request.category()));
        service.setDescription(blankToNull(request.description()));
        serviceRepository.save(service);
        return ServiceResponse.from(service);
    }

    @Transactional
    public void deleteService(Long serviceId) {
        if (!serviceRepository.existsById(serviceId)) {
            throw new AdminNotFoundException("No service found with id " + serviceId);
        }
        if (bookingRepository.existsByService_ServiceId(serviceId)) {
            throw new AdminValidationException(
                    "Cannot delete this service: it has booking history attached to it");
        }
        if (workerServiceRepository.existsByService_ServiceId(serviceId)) {
            throw new AdminValidationException(
                    "Cannot delete this service: at least one worker currently offers it");
        }
        serviceRepository.deleteById(serviceId);
    }

    // ---------------------------------------------------------------
    // Skills
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SkillResponse> listSkills() {
        return skillRepository.findAll().stream().map(SkillResponse::from).toList();
    }

    @Transactional
    public SkillResponse createSkill(SkillRequest request) {
        String name = requireName(request.skillName(), "skillName");
        if (skillRepository.findBySkillName(name).isPresent()) {
            throw new AdminValidationException("A skill named \"" + name + "\" already exists");
        }
        Skill skill = skillRepository.save(Skill.builder()
                .skillName(name)
                .category(blankToNull(request.category()))
                .description(blankToNull(request.description()))
                .build());
        return SkillResponse.from(skill);
    }

    @Transactional
    public SkillResponse updateSkill(Long skillId, SkillRequest request) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new AdminNotFoundException("No skill found with id " + skillId));
        String name = requireName(request.skillName(), "skillName");
        if (skillRepository.findBySkillNameAndSkillIdNot(name, skillId).isPresent()) {
            throw new AdminValidationException("A skill named \"" + name + "\" already exists");
        }
        skill.setSkillName(name);
        skill.setCategory(blankToNull(request.category()));
        skill.setDescription(blankToNull(request.description()));
        skillRepository.save(skill);
        return SkillResponse.from(skill);
    }

    @Transactional
    public void deleteSkill(Long skillId) {
        if (!skillRepository.existsById(skillId)) {
            throw new AdminNotFoundException("No skill found with id " + skillId);
        }
        if (bookingSkillRepository.existsBySkill_SkillId(skillId)) {
            throw new AdminValidationException(
                    "Cannot delete this skill: it is required by at least one booking's history");
        }
        if (workerSkillRepository.existsBySkill_SkillId(skillId)) {
            throw new AdminValidationException(
                    "Cannot delete this skill: at least one worker currently lists it");
        }
        skillRepository.deleteById(skillId);
    }

    // ---------------------------------------------------------------
    // Reports
    // ---------------------------------------------------------------

    /**
     * {@code to} is required by the controller (AdminController rejects a
     * missing one with 400) so it is trusted non-null here rather than
     * defaulted to {@code OffsetDateTime.now()} - that old fallback meant
     * any caller that forgot {@code to} got a report window that silently
     * slid forward with wall-clock time, so two calls moments apart could
     * disagree even with otherwise-identical inputs. {@code from} keeps
     * its default (30 days before {@code to}) since that one doesn't
     * depend on request-time "now".
     */
    @Transactional(readOnly = true)
    public AdminReportResponse getReport(OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime effectiveTo = to;
        OffsetDateTime effectiveFrom = from != null ? from : effectiveTo.minusDays(30);
        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw new AdminValidationException("\"from\" must be before \"to\"");
        }

        List<Booking> inRange = bookingRepository.findByScheduledStartBetween(effectiveFrom, effectiveTo);
        long completed = inRange.stream().filter(b -> b.getStatus() == BookingStatus.completed).count();
        long cancelled = inRange.stream().filter(b -> b.getStatus() == BookingStatus.cancelled).count();

        List<Booking> completedInRange = inRange.stream()
                .filter(b -> b.getStatus() == BookingStatus.completed)
                .toList();

        Map<Worker, Long> byWorker = completedInRange.stream()
                .filter(b -> b.getWorker() != null)
                .collect(Collectors.groupingBy(Booking::getWorker, Collectors.counting()));
        List<AdminReportResponse.TopWorker> topWorkers = byWorker.entrySet().stream()
                .sorted(Map.Entry.<Worker, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    Worker worker = entry.getKey();
                    BigDecimal avgRating = workerStatsRepository.findById(worker.getWorkerId())
                            .map(stats -> stats.getAvgRating() != null ? stats.getAvgRating() : BigDecimal.ZERO)
                            .orElse(BigDecimal.ZERO);
                    return new AdminReportResponse.TopWorker(
                            worker.getWorkerId(), worker.getUser().getFullName(), entry.getValue(), avgRating);
                })
                .toList();

        Map<com.skillshare.skillsharebackend.domain.Service, List<Booking>> byService = completedInRange.stream()
                .collect(Collectors.groupingBy(Booking::getService));
        List<AdminReportResponse.TopService> topServices = byService.entrySet().stream()
                .map(entry -> {
                    BigDecimal revenue = entry.getValue().stream()
                            .map(b -> b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new AdminReportResponse.TopService(
                            entry.getKey().getServiceId(), entry.getKey().getServiceName(),
                            entry.getValue().size(), revenue);
                })
                .sorted(Comparator.comparingLong(AdminReportResponse.TopService::bookingCount).reversed())
                .limit(5)
                .toList();

        BigDecimal revenue = paymentRepository.sumByStatusBetween(PaymentStatus.completed, effectiveFrom, effectiveTo);

        return new AdminReportResponse(
                effectiveFrom, effectiveTo, inRange.size(), completed, cancelled, revenue, topWorkers, topServices);
    }

    // ---------------------------------------------------------------

    private String requireName(String name, String fieldName) {
        if (name == null || name.isBlank()) {
            throw new AdminValidationException(fieldName + " is required");
        }
        return name.trim();
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
