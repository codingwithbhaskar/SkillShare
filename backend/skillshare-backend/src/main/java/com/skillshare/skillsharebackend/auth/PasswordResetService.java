package com.skillshare.skillsharebackend.auth;

import com.skillshare.skillsharebackend.domain.PasswordResetToken;
import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import com.skillshare.skillsharebackend.repository.PasswordResetTokenRepository;
import com.skillshare.skillsharebackend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Self-service "forgot password" flow (added alongside V7).
 *
 * <p>{@link #requestReset} always returns normally whether or not the
 * email belongs to a real account — the HTTP layer must not let a caller
 * probe which addresses are registered. When it <em>is</em> a real,
 * non-suspended account, a random token is generated, its SHA-256 hash
 * stored in {@code password_reset_tokens}, and {@link PasswordResetMailer}
 * asked to email a link carrying the plaintext token — off the request
 * thread (see that class's javadoc for why sending is never done here
 * directly).
 *
 * <p>{@link #resetPassword} hashes the presented token, looks the row up
 * by that hash, checks it's unused and unexpired, then re-hashes the new
 * password (BCrypt, via {@link PasswordEncoder}) onto the user and stamps
 * the token {@code used_at}.
 */
@Service
@Slf4j
public class PasswordResetService {

    /** How long a reset link stays valid. */
    static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    /** Minimum gap between two ACCEPTED requests for the same email - see
     *  {@link #isRateLimited}. */
    static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(60);

    private static final SecureRandom RANDOM = new SecureRandom();

    /** In-memory per-email cooldown tracker - see {@link #isRateLimited}. */
    private final Map<String, Instant> lastAcceptedRequestAt = new ConcurrentHashMap<>();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailer mailer;
    private final String frontendBaseUrl;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            PasswordResetMailer mailer,
            @Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
        // Trailing slash would produce "...app//reset-password" - harmless
        // for routing but ugly in the email, so trim it once, up front.
        this.frontendBaseUrl = trimTrailingSlash(frontendBaseUrl);
    }

    @Transactional
    public void requestReset(String email) {
        String normalized = email == null ? "" : email.strip();
        if (isRateLimited(normalized)) {
            // Same "never reveal anything" contract as the unknown-email
            // path below - the caller can't tell rate-limited apart from
            // processed, on purpose.
            log.info("[PASSWORD-RESET] rate-limited repeat request for '{}'", normalized);
            return;
        }
        userRepository.findByEmail(normalized).ifPresentOrElse(user -> {
            if (user.getStatus() == AccountStatus.suspended) {
                // A suspended account shouldn't be recoverable by the user
                // themselves — an admin reinstates it. Return silently,
                // same as the unknown-email path.
                log.info("[PASSWORD-RESET] ignoring reset request for suspended account {}", user.getUserId());
                return;
            }
            String rawToken = generateRawToken();
            PasswordResetToken token = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(sha256Hex(rawToken))
                    .expiresAt(OffsetDateTime.now().plus(TOKEN_TTL))
                    .build();
            tokenRepository.save(token);
            String resetUrl = frontendBaseUrl + "/reset-password?token=" + rawToken;
            mailer.send(user, resetUrl, TOKEN_TTL.toMinutes());
        }, () -> log.info("[PASSWORD-RESET] no account for '{}' — returning without action", normalized));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidResetTokenException("This reset link is invalid.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new AuthValidationException("password must be at least 8 characters");
        }
        PasswordResetToken token = tokenRepository.findByTokenHash(sha256Hex(rawToken))
                .orElseThrow(() -> new InvalidResetTokenException(
                        "This reset link is invalid or has already been used."));
        if (token.getUsedAt() != null) {
            throw new InvalidResetTokenException("This reset link has already been used.");
        }
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidResetTokenException("This reset link has expired. Request a new one.");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsedAt(OffsetDateTime.now());
        tokenRepository.save(token);

        log.info("[PASSWORD-RESET] password reset for user {}", user.getUserId());
    }

    /**
     * Closes a real gap: before this, {@code POST /api/auth/forgot-
     * password} had no throttling at all - a single email could be
     * spammed with reset links indefinitely (an inbox-flooding nuisance
     * at minimum, and a fast way to burn through Brevo's free-tier daily
     * send quota for every other user of this app). At most one ACCEPTED
     * request per {@link #RATE_LIMIT_WINDOW} per (normalized) email;
     * requests inside the window are silently absorbed - not surfaced as
     * a distinct "rate limited" response, so this can't be used to probe
     * whether an address is registered any more than the existing
     * unknown-email path already can't.
     *
     * <p>Entirely in-memory, keyed by email: correct for this app's
     * single-instance deployment (Render's free tier), but would need a
     * shared store (e.g. Redis with a TTL) behind more than one instance,
     * since each instance would otherwise track its own independent
     * cooldown. Stale entries are swept opportunistically on each call
     * (an entry older than ten windows is almost certainly done being
     * useful) rather than via a separate scheduled job, since the map
     * only ever holds recently-active emails in practice - no unbounded
     * growth risk worth a dedicated cleanup mechanism for this app's
     * traffic scale.
     */
    private boolean isRateLimited(String normalizedEmail) {
        Instant now = Instant.now();
        lastAcceptedRequestAt.entrySet()
                .removeIf(entry -> entry.getValue().isBefore(now.minus(RATE_LIMIT_WINDOW.multipliedBy(10))));

        Instant lastAccepted = lastAcceptedRequestAt.get(normalizedEmail);
        if (lastAccepted != null && lastAccepted.isAfter(now.minus(RATE_LIMIT_WINDOW))) {
            return true;
        }
        lastAcceptedRequestAt.put(normalizedEmail, now);
        return false;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
