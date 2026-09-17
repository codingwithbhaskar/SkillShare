package com.skillshare.skillsharebackend.auth;

import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import com.skillshare.skillsharebackend.domain.enums.UserRole;
import com.skillshare.skillsharebackend.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtAuthenticationFilter} - specifically the
 * "current status re-checked on every request" behavior added alongside
 * the admin-self-registration fix (see this class's own javadoc for why:
 * a cryptographically valid token from a since-suspended/deactivated/
 * deleted user must not authenticate, closing the "blocked users stay
 * logged in for up to 24h" gap). {@link JwtService} is mocked, so these
 * exercise the filter's own branching, not JWT parsing itself (covered by
 * {@link JwtServiceTest}). Calls {@code doFilterInternal} directly - it's
 * {@code protected}, reachable from a same-package test.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;
    @Mock
    private Claims claims;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, userRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private User userWithStatus(AccountStatus status) {
        return User.builder().userId(1L).role(UserRole.customer).status(status).build();
    }

    @Test
    void authenticates_whenTokenValidAndUserActive() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtService.parseClaims("good-token")).thenReturn(Optional.of(claims));
        when(claims.getSubject()).thenReturn("1");
        when(claims.get("role", String.class)).thenReturn("customer");
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithStatus(AccountStatus.active)));

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doesNotAuthenticate_whenUserSuspended() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtService.parseClaims("good-token")).thenReturn(Optional.of(claims));
        when(claims.getSubject()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithStatus(AccountStatus.suspended)));

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response); // still not blocked outright - see class javadoc
    }

    @Test
    void doesNotAuthenticate_whenUserDeactivated() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtService.parseClaims("good-token")).thenReturn(Optional.of(claims));
        when(claims.getSubject()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithStatus(AccountStatus.inactive)));

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doesNotAuthenticate_whenUserNoLongerExists() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtService.parseClaims("good-token")).thenReturn(Optional.of(claims));
        when(claims.getSubject()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doesNotAuthenticate_whenNoAuthorizationHeader() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userRepository, never()).findById(any());
    }
}
