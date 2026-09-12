package com.skillshare.skillsharebackend.auth;

import com.skillshare.skillsharebackend.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Sends the password-reset email. Split out of {@link PasswordResetService}
 * specifically so {@link #send} can be {@code @Async} - {@code @Async}
 * only works through a Spring AOP proxy, which self-invocation (a class
 * calling its own method) bypasses entirely, so this has to be a separate
 * bean.
 *
 * <p>Why async at all: SMTP is a slow, sometimes-unreliable external call
 * with no timeout configured anywhere in the stack by default (same class
 * of gap as the database hang fixed alongside this - see
 * {@code application.yml}'s {@code spring.datasource.hikari} comment).
 * Confirmed live 2026-09-12: a real SMTP provider mid-configuration froze
 * the entire {@code POST /api/auth/forgot-password} request indefinitely
 * on the browser's "Sending…" state. Running the send here, off the
 * request thread, means a slow or broken SMTP endpoint can never block
 * the HTTP response - {@link PasswordResetService#requestReset} has
 * already committed the token and the controller has already returned
 * 202 by the time this runs.
 */
@Component
@Slf4j
public class PasswordResetMailer {

    private final JavaMailSender mailSender;
    private final boolean mailEnabled;
    private final String mailFrom;

    public PasswordResetMailer(
            JavaMailSender mailSender,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${app.mail.from:SkillShare <no-reply@skillshare.local>}") String mailFrom) {
        this.mailSender = mailSender;
        this.mailEnabled = mailHost != null && !mailHost.isBlank();
        this.mailFrom = mailFrom;
    }

    @Async
    public void send(User user, String resetUrl, long tokenTtlMinutes) {
        if (!mailEnabled) {
            log.warn("[PASSWORD-RESET] spring.mail.host is not set — not sending email. "
                    + "Reset link for {} (valid {} min): {}",
                    user.getEmail(), tokenTtlMinutes, resetUrl);
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
                """.formatted(user.getFullName(), tokenTtlMinutes, resetUrl));
        try {
            mailSender.send(message);
            log.info("[PASSWORD-RESET] reset email sent to {}", user.getEmail());
        } catch (MailException e) {
            // Swallow: this already runs after the caller got its 202, and
            // surfacing "send failed" to them would reveal that this email
            // is registered anyway. Operators see it in the log.
            log.error("[PASSWORD-RESET] failed to send reset email to {}: {}", user.getEmail(), e.getMessage());
        }
    }
}
