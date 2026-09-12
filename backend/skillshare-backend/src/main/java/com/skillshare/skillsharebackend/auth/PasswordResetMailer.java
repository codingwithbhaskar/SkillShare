package com.skillshare.skillsharebackend.auth;

import com.skillshare.skillsharebackend.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends the password-reset email via Brevo's HTTPS Transactional Email
 * API ({@code POST https://api.brevo.com/v3/smtp/email}, wired up as the
 * {@code brevoRestClient} bean in {@code BrevoConfig}) - deliberately NOT
 * raw SMTP. Confirmed live 2026-09-12: Render blocks outbound SMTP (ports
 * 587/465/25) on its free tier as an anti-abuse measure (a common
 * PaaS/cloud policy), so a {@code JavaMailSender}-based approach could
 * never work there - every attempt timed out on the TCP connect itself.
 * The HTTPS API needs only an API key and travels over port 443, which is
 * never blocked.
 *
 * <p>Split out of {@link PasswordResetService} so {@link #send} can be
 * {@code @Async} - {@code @Async} only works through a Spring AOP proxy,
 * which self-invocation (a class calling its own method) bypasses
 * entirely, so this has to be a separate bean. Running the send here, off
 * the request thread, means a slow or broken mail provider can never
 * block the HTTP response - confirmed live 2026-09-12 that this was a
 * real, user-visible bug ("Sending…" hanging forever) before this class
 * existed.
 *
 * <p>With {@code brevo.api-key} unset, the link is logged instead of
 * emailed - same graceful-fallback stance as every other optional
 * integration in this project.
 */
@Component
@Slf4j
public class PasswordResetMailer {

    private static final Pattern FROM_PATTERN = Pattern.compile("^(.*)<(.+)>$");

    private final RestClient restClient;
    private final String apiKey;
    private final String fromName;
    private final String fromEmail;

    public PasswordResetMailer(
            RestClient brevoRestClient,
            @Value("${brevo.api-key:}") String apiKey,
            @Value("${app.mail.from:SkillShare <no-reply@skillshare.local>}") String mailFrom) {
        this.restClient = brevoRestClient;
        this.apiKey = apiKey;

        Matcher matcher = FROM_PATTERN.matcher(mailFrom == null ? "" : mailFrom.trim());
        if (matcher.matches()) {
            this.fromName = matcher.group(1).trim();
            this.fromEmail = matcher.group(2).trim();
        } else {
            this.fromName = "SkillShare";
            this.fromEmail = mailFrom == null ? "" : mailFrom.trim();
        }
    }

    @Async
    public void send(User user, String resetUrl, long tokenTtlMinutes) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[PASSWORD-RESET] brevo.api-key is not set — not sending email. "
                    + "Reset link for {} (valid {} min): {}",
                    user.getEmail(), tokenTtlMinutes, resetUrl);
            return;
        }
        String body = """
                Hi %s,

                We received a request to reset your SkillShare password. Open the link
                below within %d minutes to choose a new one:

                %s

                If you didn't ask for this, you can safely ignore this email — your
                password won't change.

                — SkillShare
                """.formatted(user.getFullName(), tokenTtlMinutes, resetUrl);
        try {
            restClient.post()
                    .uri("/smtp/email")
                    // .headers(Consumer<HttpHeaders>) rather than the
                    // varargs .header(String, String...) overload - not
                    // just style, Mockito's RETURNS_DEEP_STUBS can't
                    // synthesize a stub return value through a varargs
                    // method (confirmed live in PasswordResetMailerTest).
                    .headers(headers -> headers.set("api-key", apiKey))
                    .body(new BrevoEmailRequest(
                            new BrevoContact(fromName, fromEmail),
                            List.of(new BrevoContact(user.getFullName(), user.getEmail())),
                            "Reset your SkillShare password",
                            body))
                    .retrieve()
                    .toBodilessEntity();
            log.info("[PASSWORD-RESET] reset email sent to {}", user.getEmail());
        } catch (RestClientException e) {
            // Swallow: this already runs after the caller got its 202, and
            // surfacing "send failed" to them would reveal that this email
            // is registered anyway. Operators see it in the log.
            log.error("[PASSWORD-RESET] failed to send reset email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    /** Brevo's API takes {@code name}/{@code email} pairs for both the
     *  sender and each recipient - same shape either way. */
    private record BrevoContact(String name, String email) {
    }

    /** Body of {@code POST /v3/smtp/email}. Brevo's API also accepts
     *  {@code htmlContent}; a plain-text body is enough here. */
    private record BrevoEmailRequest(BrevoContact sender, List<BrevoContact> to, String subject, String textContent) {
    }
}
