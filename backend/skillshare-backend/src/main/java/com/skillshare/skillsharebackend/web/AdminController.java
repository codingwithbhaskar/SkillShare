package com.skillshare.skillsharebackend.web;

import com.skillshare.skillsharebackend.admin.AdminService;
import com.skillshare.skillsharebackend.admin.AdminValidationException;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import com.skillshare.skillsharebackend.domain.enums.BookingStatus;
import com.skillshare.skillsharebackend.domain.enums.UserRole;
import com.skillshare.skillsharebackend.web.dto.AdminBookingResponse;
import com.skillshare.skillsharebackend.web.dto.AdminReportResponse;
import com.skillshare.skillsharebackend.web.dto.AdminStatsResponse;
import com.skillshare.skillsharebackend.web.dto.AdminUserResponse;
import com.skillshare.skillsharebackend.web.dto.ServiceRequest;
import com.skillshare.skillsharebackend.web.dto.ServiceResponse;
import com.skillshare.skillsharebackend.web.dto.SkillRequest;
import com.skillshare.skillsharebackend.web.dto.SkillResponse;
import com.skillshare.skillsharebackend.web.dto.UpdateUserStatusRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Admin panel HTTP surface, wrapping {@link AdminService}. Closes the
 * "no admin-specific backend endpoints exist yet" gap {@code
 * AdminPage.jsx}'s Phase 8 placeholder documented explicitly.
 *
 * <p>Gated to ROLE_ADMIN entirely at the path level by {@code
 * SecurityConfig}'s {@code /api/admin/**} rule - unlike {@code
 * WorkerDashboardController}, there is no per-resource ownership check
 * needed here (an admin acting on any user/service/skill/booking is the
 * point of the panel, not a gap to close).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    public AdminStatsResponse stats() {
        return adminService.getStats();
    }

    @GetMapping("/users")
    public List<AdminUserResponse> users(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) String search) {
        return adminService.listUsers(role, status, search);
    }

    @PatchMapping("/users/{userId}/status")
    public AdminUserResponse updateUserStatus(@PathVariable Long userId, @RequestBody UpdateUserStatusRequest request) {
        return adminService.updateUserStatus(userId, request.status());
    }

    @GetMapping("/bookings")
    public List<AdminBookingResponse> bookings(@RequestParam(required = false) BookingStatus status) {
        return adminService.listBookings(status);
    }

    @GetMapping("/services")
    public List<ServiceResponse> services() {
        return adminService.listServices();
    }

    @PostMapping("/services")
    public ServiceResponse createService(@RequestBody ServiceRequest request) {
        return adminService.createService(request);
    }

    @PutMapping("/services/{serviceId}")
    public ServiceResponse updateService(@PathVariable Long serviceId, @RequestBody ServiceRequest request) {
        return adminService.updateService(serviceId, request);
    }

    @DeleteMapping("/services/{serviceId}")
    public void deleteService(@PathVariable Long serviceId) {
        adminService.deleteService(serviceId);
    }

    @GetMapping("/skills")
    public List<SkillResponse> skills() {
        return adminService.listSkills();
    }

    @PostMapping("/skills")
    public SkillResponse createSkill(@RequestBody SkillRequest request) {
        return adminService.createSkill(request);
    }

    @PutMapping("/skills/{skillId}")
    public SkillResponse updateSkill(@PathVariable Long skillId, @RequestBody SkillRequest request) {
        return adminService.updateSkill(skillId, request);
    }

    @DeleteMapping("/skills/{skillId}")
    public void deleteSkill(@PathVariable Long skillId) {
        adminService.deleteSkill(skillId);
    }

    /**
     * {@code from}/{@code to} are plain ISO-8601 offset-date-time
     * strings, parsed here rather than declared as {@code OffsetDateTime}
     * request-param types directly - keeps this endpoint independent of
     * whichever date/time {@code Converter}s happen to be registered in
     * Spring MVC's conversion service for query parameters (unlike a
     * JSON request body, which always goes through Jackson's
     * JavaTimeModule regardless - see {@code CreateBookingRequest}).
     *
     * <p>{@code to} is REQUIRED (400 if missing/blank), on purpose: {@code
     * AdminService.getReport} used to silently default a missing {@code to}
     * to {@code OffsetDateTime.now()}, meaning any caller that forgot to
     * pass it would get a report window that keeps sliding forward with
     * real time - two otherwise-identical requests a few seconds apart
     * could return different totals. The current UI (AdminReportsPage.jsx)
     * always sends both, so this never actually fired from the app, but
     * it was a live footgun for any other caller. Rejecting a missing
     * {@code to} outright closes it off entirely rather than leaving a
     * silent, time-dependent default in place. {@code from} keeps its
     * default (30 days before {@code to}) since that default doesn't
     * depend on wall-clock time at request time.
     */
    @GetMapping("/reports")
    public AdminReportResponse reports(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        if (to == null || to.isBlank()) {
            throw new AdminValidationException("\"to\" is required, e.g. 2026-08-01T00:00:00Z");
        }
        OffsetDateTime parsedFrom = parseOffsetDateTime(from, "from");
        OffsetDateTime parsedTo = parseOffsetDateTime(to, "to");
        return adminService.getReport(parsedFrom, parsedTo);
    }

    private OffsetDateTime parseOffsetDateTime(String value, String paramName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw new AdminValidationException(
                    "\"" + paramName + "\" must be an ISO-8601 offset date-time, e.g. 2026-08-01T00:00:00Z");
        }
    }
}
