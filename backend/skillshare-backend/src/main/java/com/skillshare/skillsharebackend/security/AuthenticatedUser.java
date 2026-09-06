package com.skillshare.skillsharebackend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * The caller of the current request, resolved from Spring Security's
 * {@link Authentication} - closes the "existing endpoints don't yet
 * cross-check client-supplied ids against the authenticated principal"
 * gap documented in dev-status-and-next-steps.md's Phase 7 section and
 * carried forward into phase-8-frontend-in-progress.md's full retest
 * findings.
 *
 * <p>{@code userId} mirrors {@link JwtAuthenticationFilter}'s existing
 * convention: the JWT subject claim, i.e. {@code users.user_id} - NOT a
 * worker id. {@code role} is the lowercase role name (matching
 * {@code UserRole}'s enum constants, e.g. {@code "customer"},
 * {@code "worker"}, {@code "admin"}), read back off the single
 * {@code ROLE_<ROLE>} authority {@code JwtAuthenticationFilter} grants.
 */
public record AuthenticatedUser(Long userId, String role) {

    public static AuthenticatedUser from(Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.replace("ROLE_", "").toLowerCase())
                .orElse("");
        return new AuthenticatedUser(userId, role);
    }

    public boolean isAdmin() {
        return "admin".equals(role);
    }

    public boolean isWorker() {
        return "worker".equals(role);
    }

    public boolean isCustomer() {
        return "customer".equals(role);
    }
}
