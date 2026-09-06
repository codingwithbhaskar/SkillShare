package com.skillshare.skillsharebackend.web;

import com.skillshare.skillsharebackend.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * General "my account" HTTP surface for any authenticated user
 * (customer/worker/admin) - separate from {@link AuthController}'s
 * pre-token register/login endpoints (which stay permitAll) and from
 * {@code WorkerDashboardController}'s worker-only {@code /api/workers/me}.
 * Matched by SecurityConfig's default {@code .anyRequest().authenticated()}
 * rule - no dedicated security rule needed.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    /** Self-service account deactivation - see
     *  {@link AuthService#deactivateSelf} for the exact rules (blocked
     *  for admins and already-suspended accounts). */
    @PostMapping("/me/deactivate")
    public void deactivateMe(Authentication authentication) {
        authService.deactivateSelf(Long.valueOf(authentication.getName()));
    }
}
