package com.skillshare.skillsharebackend.auth;

import com.skillshare.skillsharebackend.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and validates JWT bearer tokens (Phase 7 - authentication).
 *
 * Signing key: HMAC-SHA256, sourced from the {@code JWT_SECRET} env var
 * ({@code application-dev.yml -> jwt.secret}), following the same
 * never-hardcode-secrets convention as {@code SKILLSHARE_DB_PASSWORD} and
 * the Razorpay credentials. Unlike those, though, this key isn't tied to
 * an external account - if {@code JWT_SECRET} is unset, a
 * cryptographically random key is generated once at application startup
 * instead of failing to boot. That's fine for local development and
 * testing ({@code mvnw.cmd test}'s context-load test doesn't need a real
 * secret to pass), but it means every restart invalidates all previously
 * issued tokens. Set {@code JWT_SECRET} to a real, stable value for
 * anything that needs tokens to survive a restart.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret:}") String configuredSecret,
            @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        this.key = configuredSecret.isBlank()
                ? Jwts.SIG.HS256.key().build()
                : Keys.hmacShaKeyFor(configuredSecret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** Encodes the user's id, role, email, and full name as claims -
     *  enough for the frontend to render role-based UI and for
     *  {@link JwtAuthenticationFilter} to authorize requests without a
     *  DB round-trip on every call. */
    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUserId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("fullName", user.getFullName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    /** Returns the parsed claims if the token is structurally valid,
     *  correctly signed, and not expired; empty otherwise. Never throws -
     *  {@link JwtAuthenticationFilter} treats any failure as "not
     *  authenticated" rather than surfacing a 500. */
    public Optional<Claims> parseClaims(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
