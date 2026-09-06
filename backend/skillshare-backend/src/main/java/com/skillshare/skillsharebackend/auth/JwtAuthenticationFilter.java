package com.skillshare.skillsharebackend.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Reads the {@code Authorization: Bearer <token>} header, validates it via
 * {@link JwtService}, and - if valid - populates the
 * {@link SecurityContextHolder} with an authenticated principal carrying a
 * single {@code ROLE_<role>} authority (e.g. {@code ROLE_WORKER}), so
 * {@code @PreAuthorize}/{@code hasRole(...)} checks and
 * {@code SecurityConfig}'s {@code authorizeHttpRequests} rules work.
 *
 * The principal is the user's id (as a {@code String}, from the JWT
 * subject claim) rather than a re-fetched {@code User} entity - every
 * downstream controller/service in this project already takes explicit
 * id parameters (e.g. {@code customerId} on {@code CreateBookingRequest}),
 * so this filter's job is purely "is this request authenticated, and as
 * which role" - not "load the full user." See dev-status-and-next-
 * steps.md's Phase 7 notes for the follow-up gap this implies (existing
 * endpoints don't yet cross-check a request's client-supplied ids against
 * the authenticated principal).
 *
 * Missing/malformed/expired tokens are simply not authenticated (the
 * filter chain continues with no {@code SecurityContext} set) rather than
 * rejected outright here - {@code SecurityConfig}'s
 * {@code authorizeHttpRequests} rules are what actually turn "not
 * authenticated" into a 401/403 for protected routes.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            Optional<Claims> claims = jwtService.parseClaims(token);
            // Deliberately unconditional: a valid bearer token always wins,
            // overwriting whatever the SecurityContext currently holds
            // (including a leftover anonymous token from earlier in the
            // chain) rather than only filling in when empty. The original
            // "only if null" guard was fragile against exactly that case -
            // confirmed live 2026-08-30: an authenticated-but-wrong-role
            // request came back 401 (entry point) instead of the correct
            // 403 (access-denied handler), consistent with the real
            // authentication never having been set for that request.
            if (claims.isPresent()) {
                String userId = claims.get().getSubject();
                String role = claims.get().get("role", String.class);
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
