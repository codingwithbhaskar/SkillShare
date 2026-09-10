package com.skillshare.skillsharebackend.auth;

import com.skillshare.skillsharebackend.domain.PasswordResetToken;
import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import com.skillshare.skillsharebackend.repository.PasswordResetTokenRepository;
import com.skillshare.skillsharebackend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Self-service "forgot password" flow (added alongside V7).
 *
 * <p>{@link #requestReset} always returns normally whether or not the
 * email belongs to a real account — the HTTP layer must not let a caller
 * probe which addresses are registered. When it <em>is</em> a real,
 * non-suspended account, a random token is generated, its SHA-256 hash
 * stored in {@code password_reset_tokens}, and a link carrying the
 * plaintext token emailed to the user.
 *
 * <p>{@link #resetPassword} hashes the presented token, looks the row up
 * by that hash, checks it's unused and unexpired, then re-hashes the new
 * password (BCrypt, via {@link PasswordEncoder}) onto the user and stamps
 * the token {@code used_at}.
 *
 * <p>Email delivery: if {@code spring.mail.host} is configured a real
 * message is sent; otherwise the link is written to the log at WARN so
 * the flow is still testable locally without an SMTP account (same
 * "logging placeholder" stance as {@code LoggingNotificationSender}).
 */
@Service
@Slf4j
public class PasswordResetService {

    /** How long a reset link stays valid. */
    static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final boolean mailEnabled;
    private final String mailFrom;
    private final String frontendBaseUrl;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${app.mail.from:SkillShare <no-reply@skillshare.local>}") String mailFrom,
            @Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.mailEnabled = mailHost != null && !mailHost.isBlank();
        this.mailFrom = mailFrom;
        // Trailing slash would produce "...app//reset-password"; harmless
        // for routing but ugly in the email, so trim it.
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
    }

    @Transactional
    public void requestReset(String email) {
        String normalized = email == null ? "" : email.strip();
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
            sendResetEmail(user, frontendBaseUrl + "/reset-password?token=" + rawToken);
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

    private void sendResetEmail(User user, String resetUrl) {
        if (!mailEnabled) {
            log.warn("[PASSWORD-RESET] spring.mail.host is not set — not sending email. "
                    + "Reset link for {} (valid {} min): {}",
                    user.getEmail(), TOKEN_TTL.toMinutes(), resetUrl);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(user.getEmail());
        message.setSubject("Reset your SkillShare password");
        message.setText("""
                Hi %s,

                We received a request to reset your SkillShare password. Open the link
                below within %d minutes to choose a new one:

                %s

                If you didn't ask for this, you can safely ignore this email — your
                password won't change.

                — SkillShare
                """.formatted(user.getFullName(), TOKEN_TTL.toMinutes(), resetUrl));
        try {
            mailSender.send(message);
            log.info("[PASSWORD-RESET] reset email sent to {}", user.getEmail());
        } catch (MailException e) {
            // Swallow: surfacing "send failed" to the caller would reveal
            // that this email is registered. Operators see it in the log.
            log.error("[PASSWORD-RESET] failed to send reset email to {}: {}", user.getEmail(), e.getMessage());
        }
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
