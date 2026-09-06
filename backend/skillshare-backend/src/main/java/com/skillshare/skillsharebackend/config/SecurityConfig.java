package com.skillshare.skillsharebackend.config;

import com.skillshare.skillsharebackend.auth.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Phase 7 - real JWT authentication, replacing the permissive placeholder
 * from Phase 2 (see git history / phase-6-payment-gateway-code-complete.md
 * for how long that stayed in place, and dev-status-and-next-steps.md's
 * Phase 7 section for the full design rationale).
 *
 * Rules, in order:
 * - {@code /api/auth/**} - open. Registration and login can't require a
 *   token to obtain a token.
 * - {@code /api/payments/webhook} - open. Razorpay's servers call this
 *   directly, server-to-server; it authenticates itself via its own HMAC
 *   signature header ({@code PaymentService.handleWebhook}), not a user
 *   JWT.
 * - {@code /api/workers/**} (the worker dashboard) - requires WORKER or
 *   ADMIN at the path level. A concrete, demonstrated example of coarse
 *   role-based access - most other endpoints are left at "any
 *   authenticated user" here rather than growing this list into a full
 *   per-path authorization matrix.
 * - {@code /api/admin/**} (the admin panel) - requires ADMIN at the path
 *   level, same coarse pattern as the worker-dashboard rule above. Every
 *   admin endpoint acts platform-wide by design, so unlike the worker
 *   dashboard there's no per-resource ownership check layered on top in
 *   the service ({@code AdminService}).
 * - Everything else under {@code /api/**} - requires authentication (any
 *   valid role).
 *
 * <p>Fine-grained, per-resource authorization (a customer may only
 * cancel their OWN booking, a worker may only start/complete a booking
 * THEY were assigned, a worker may only view THEIR OWN dashboard) is
 * deliberately NOT expressed here as more path rules - a coarse
 * {@code hasRole(...)} rule can't tell "my booking" from "someone else's
 * booking" for the same URL pattern. That check lives one layer down,
 * in the service methods themselves, via {@code AuthenticatedUser} and
 * its ownership-check overloads (see {@code BookingService}, {@code
 * ReviewService}, {@code AllocationService}, {@code
 * WorkerDashboardService}) - closing the "existing endpoints don't yet
 * cross-check client-supplied ids against the authenticated principal"
 * gap this class's Phase 7 javadoc originally flagged as still open.
 *
 * Session creation is disabled ({@code STATELESS}) since JWT carries all
 * the state needed per request - no server-side session store. CSRF stays
 * disabled, same reasoning as before: this is a stateless JSON API, not a
 * cookie/session-based browser form flow.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** Comma-separated list of origins the browser-based frontend is served from.
     *  Defaults to the Vite dev server (Phase 8). Override via
     *  {@code app.cors.allowed-origins} (env var {@code APP_CORS_ALLOWED_ORIGINS})
     *  once a real deployment origin exists. */
    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS is a browser-only concern - PowerShell/curl (everything Phases
     * 1-7 were live-tested with before now) never sends the preflight
     * OPTIONS request or checks Access-Control-Allow-Origin, so this gap
     * was invisible until Phase 8's real browser frontend hit it: every
     * request from the Vite dev server (localhost:5173) to the API
     * (localhost:8080) failed as a generic Axios "Network Error" before
     * even reaching a controller, because Spring Security has no CORS
     * configuration by default. Fixed by registering an explicit
     * CorsConfigurationSource and enabling it in the filter chain below.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(false); // JWT travels via Authorization header, not cookies
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/payments/webhook").permitAll()
                        // Required for a STATELESS app: sendError() from
                        // authenticationEntryPoint/accessDeniedHandler below
                        // triggers an internal servlet forward to /error,
                        // which is a SECOND pass through this whole filter
                        // chain. JwtAuthenticationFilter (a OncePerRequestFilter)
                        // skips itself on that error dispatch by default, and
                        // with no HttpSession to carry the original
                        // authentication across the forward, /error would
                        // otherwise land anonymous and get rejected by
                        // .anyRequest().authenticated() below - clobbering
                        // the real status with a 401 regardless of what the
                        // original denial actually was. Confirmed live
                        // 2026-08-30: every access-denied case came back 401
                        // instead of 403 until this was added, matching
                        // https://github.com/spring-projects/spring-boot/issues/31852.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/workers/**").hasAnyRole("WORKER", "ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Without this, Spring Security's default fallback entry point
                // (Http403ForbiddenEntryPoint - there's no login page/httpBasic
                // configured to challenge with) turns EVERY auth failure into
                // 403, including a request with no token at all. Explicit here
                // so the frontend can tell "not logged in" (401) apart from
                // "logged in, wrong role" (403) - confirmed live 2026-08-30:
                // a no-token request against a protected endpoint came back
                // 403 before this was added.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Insufficient role")));
        return http.build();
    }
}
