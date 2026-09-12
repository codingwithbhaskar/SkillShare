package com.skillshare.skillsharebackend.auth;

import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import com.skillshare.skillsharebackend.domain.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link PasswordResetMailer} - the actual email content
 * and the "no SMTP configured" / "SMTP failed" fallbacks. Note: {@code
 * @Async} has no effect here since these tests call the plain object
 * directly rather than through a Spring-created proxy, so sends happen
 * synchronously and are safe to assert on immediately.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetMailerTest {

    @Mock
    private JavaMailSender mailSender;

    private User user() {
        return User.builder()
                .userId(1L).role(UserRole.customer).fullName("Asha Patil")
                .email("asha@example.com").passwordHash("hash").status(AccountStatus.active)
                .build();
    }

    @Test
    void send_withSmtpConfigured_buildsAndSendsCorrectMessage() {
        PasswordResetMailer mailer = new PasswordResetMailer(
                mailSender, "smtp.example.com", "SkillShare <no-reply@skillshare.local>");

        mailer.send(user(), "https://app.example.com/reset-password?token=abc123", 30);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertEquals("SkillShare <no-reply@skillshare.local>", message.getFrom());
        assertEquals("asha@example.com", message.getTo()[0]);
        assertTrue(message.getSubject().toLowerCase().contains("reset"));
        assertNotNull(message.getText());
        assertTrue(message.getText().contains("https://app.example.com/reset-password?token=abc123"));
        assertTrue(message.getText().contains("30 minutes"));
    }

    @Test
    void send_noSmtpConfigured_doesNotCallMailSender() {
        PasswordResetMailer mailer = new PasswordResetMailer(
                mailSender, "", "SkillShare <no-reply@skillshare.local>");

        mailer.send(user(), "https://app.example.com/reset-password?token=abc123", 30);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void send_mailSendFailure_isSwallowed() {
        PasswordResetMailer mailer = new PasswordResetMailer(
                mailSender, "smtp.example.com", "SkillShare <no-reply@skillshare.local>");
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        mailer.send(user(), "https://app.example.com/reset-password?token=abc123", 30); // must not throw
    }
}
