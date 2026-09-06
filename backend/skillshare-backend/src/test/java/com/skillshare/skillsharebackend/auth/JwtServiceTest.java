package com.skillshare.skillsharebackend.auth;

import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import com.skillshare.skillsharebackend.domain.enums.UserRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real (non-mocked) round-trip tests for {@link JwtService} - unlike
 * {@code PaymentServiceTest}'s Razorpay boundary, there's no SDK
 * constructor restriction here, so the actual generate-then-parse path
 * IS fully testable without a live environment.
 */
class JwtServiceTest {

    private JwtService newService() {
        // Blank secret -> JwtService generates its own random key, exactly
        // as it does at real application startup when JWT_SECRET is unset.
        return new JwtService("", 86400000L);
    }

    private User testUser() {
        return User.builder()
                .userId(7L)
                .role(UserRole.worker)
                .fullName("Ravi Pawar")
                .email("ravi@example.com")
                .passwordHash("hashed")
                .status(AccountStatus.active)
                .build();
    }

    @Test
    void generateThenParse_roundTripsAllClaims() {
        JwtService service = newService();
        String token = service.generateToken(testUser());

        Optional<Claims> claims = service.parseClaims(token);

        assertTrue(claims.isPresent());
        assertEquals("7", claims.get().getSubject());
        assertEquals("worker", claims.get().get("role", String.class));
        assertEquals("ravi@example.com", claims.get().get("email", String.class));
        assertEquals("Ravi Pawar", claims.get().get("fullName", String.class));
    }

    @Test
    void parseClaims_rejectsGarbageToken() {
        JwtService service = newService();
        assertTrue(service.parseClaims("not-a-real-jwt").isEmpty());
    }

    @Test
    void parseClaims_rejectsTokenSignedWithADifferentKey() {
        JwtService serviceA = newService();
        JwtService serviceB = newService();
        String token = serviceA.generateToken(testUser());

        // serviceB has its own independently-generated random key, so a
        // token signed by serviceA must fail verification under serviceB.
        assertTrue(serviceB.parseClaims(token).isEmpty());
    }

    @Test
    void parseClaims_rejectsAlreadyExpiredToken() {
        JwtService service = new JwtService("", -1000L); // expires 1s in the past, immediately
        String token = service.generateToken(testUser());

        assertTrue(service.parseClaims(token).isEmpty());
    }
}
