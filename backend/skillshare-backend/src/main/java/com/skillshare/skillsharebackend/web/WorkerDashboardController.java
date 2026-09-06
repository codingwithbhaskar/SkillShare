package com.skillshare.skillsharebackend.web;

import com.skillshare.skillsharebackend.dashboard.WorkerDashboardService;
import com.skillshare.skillsharebackend.security.AuthenticatedUser;
import com.skillshare.skillsharebackend.web.dto.BookingSummaryResponse;
import com.skillshare.skillsharebackend.web.dto.ReviewResponse;
import com.skillshare.skillsharebackend.web.dto.UpdateWorkerProfileRequest;
import com.skillshare.skillsharebackend.web.dto.WorkerProfileResponse;
import com.skillshare.skillsharebackend.web.dto.WorkerStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Phase 5 - worker dashboard HTTP surface, wrapping
 *  {@link WorkerDashboardService}. The {workerId}-scoped endpoints below
 *  are restricted (via {@link WorkerDashboardService}'s
 *  {@link AuthenticatedUser}-aware overloads) to that worker's own
 *  account or an admin - closing the "any worker can view any other
 *  worker's dashboard by guessing a workerId" gap found in the Phase 8
 *  full retest, on top of SecurityConfig's existing coarse WORKER/ADMIN
 *  role rule. */
@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
public class WorkerDashboardController {

    private final WorkerDashboardService workerDashboardService;

    /** Closes the Phase 7 documented gap: lets a logged-in worker
     *  discover their own workerId (and bare profile) without already
     *  knowing it, so the frontend can then call the {workerId}-scoped
     *  endpoints below. Matched by SecurityConfig's existing
     *  {@code /api/workers/**} -> WORKER/ADMIN rule, no change needed
     *  there. */
    @GetMapping("/me")
    public WorkerProfileResponse me(Authentication authentication) {
        return workerDashboardService.getMyProfile(Long.valueOf(authentication.getName()));
    }

    /** Closes the "no self-service complete-my-profile" gap documented
     *  since Phase 7: lets the logged-in worker set their location, bio,
     *  hourly experience/rate, skills, and per-service rates in one call.
     *  Always acts on the caller's own worker row - there is no path
     *  parameter to spoof here, unlike the {workerId}-scoped endpoints
     *  below. */
    @PutMapping("/me")
    public WorkerProfileResponse updateMe(
            @RequestBody UpdateWorkerProfileRequest request, Authentication authentication) {
        return workerDashboardService.updateMyProfile(Long.valueOf(authentication.getName()), request);
    }

    @GetMapping("/{workerId}/bookings")
    public List<BookingSummaryResponse> bookings(@PathVariable Long workerId, Authentication authentication) {
        return workerDashboardService.getBookings(workerId, AuthenticatedUser.from(authentication));
    }

    @GetMapping("/{workerId}/reviews")
    public List<ReviewResponse> reviews(@PathVariable Long workerId, Authentication authentication) {
        return workerDashboardService.getReviews(workerId, AuthenticatedUser.from(authentication));
    }

    @GetMapping("/{workerId}/stats")
    public WorkerStatsResponse stats(@PathVariable Long workerId, Authentication authentication) {
        return workerDashboardService.getStats(workerId, AuthenticatedUser.from(authentication));
    }
}
