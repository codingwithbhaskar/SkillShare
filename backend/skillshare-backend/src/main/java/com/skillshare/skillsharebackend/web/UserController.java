package com.skillshare.skillsharebackend.web;

import com.skillshare.skillsharebackend.auth.AuthService;
import com.skillshare.skillsharebackend.web.dto.AuthResponse;
import com.skillshare.skillsharebackend.web.dto.ChangePasswordRequest;
import com.skillshare.skillsharebackend.web.dto.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * General "my account" HTTP surface for any authenticated user
 * (customer/worker/admin) - separate from {@link AuthController}'s
 * pre-token register/login endpoints (which stay permitAll) and from
 * {@code WorkerDashboardController}'s worker-only {@code /api/workers/me}
 * (bio/rate/location/skills/availability - domain fields only a worker
 * has). Matched by SecurityConfig's default {@code
 * .anyRequest().authenticated()} rule - no dedicated security rule
 * needed, every role can reach every endpoint here.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    /** Universal profile edit (fullName/phone) - see
     *  {@link com.skillshare.skillsharebackend.web.dto.UpdateProfileRequest}
     *  for why those two fields specifically. Returns a fresh token/
     *  {@link AuthResponse} so the frontend's stored auth updates without
     *  a re-login. */
    @PutMapping("/me")
    public AuthResponse updateMe(Authentication authentication, @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(Long.valueOf(authentication.getName()), request);
    }

    /** Self-service password change - requires the current password (see
     *  {@link AuthService#changePassword}). */
    @PostMapping("/me/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePasswordMe(Authentication authentication, @RequestBody ChangePasswordRequest request) {
        authService.changePassword(Long.valueOf(authentication.getName()), request);
    }

    /** Self-service account deactivation - see
     *  {@link AuthService#deactivateSelf} for the exact rules (blocked
     *  for admins and already-suspended accounts). */
    @PostMapping("/me/deactivate")
    public void deactivateMe(Authentication authentication) {
        authService.deactivateSelf(Long.valueOf(authentication.getName()));
    }
}
