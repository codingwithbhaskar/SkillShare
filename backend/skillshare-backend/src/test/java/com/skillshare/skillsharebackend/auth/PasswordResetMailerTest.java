package com.skillshare.skillsharebackend.auth;

import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import com.skillshare.skillsharebackend.domain.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link PasswordResetMailer}, using {@link
 * MockRestServiceServer} (Spring's own tool for asserting on outgoing
 * {@link RestClient} calls) rather than mocking {@code RestClient}
 * directly - its fluent, generically-self-typed interface doesn't play
 * well with Mockito's {@code RETURNS_DEEP_STUBS} (confirmed while writing
 * this: every chained call past {@code .headers(...)} came back null).
 * Note: {@code @Async} has no effect here since these tests call the
 * plain object directly rather than through a Spring-created proxy, so
 * sends happen synchronously.
 */
class PasswordResetMailerTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("https://api.brevo.com/v3");
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private User user() {
        return User.builder()
                .userId(1L).role(UserRole.customer).fullName("Asha Patil")
                .email("asha@example.com").passwordHash("hash").status(AccountStatus.active)
                .build();
    }

    @Test
    void send_withApiKeyConfigured_postsExpectedRequestToBrevo() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "test-api-key"))
                .andExpect(content().string(containsString("\"email\":\"asha@example.com\"")))
                .andExpect(content().string(containsString(
                        "https://app.example.com/reset-password?token=abc123")))
                .andExpect(content().string(containsString("Reset your SkillShare password")))
                .andRespond(withSuccess());

        PasswordResetMailer mailer = new PasswordResetMailer(
                builder.build(), "test-api-key", "SkillShare <no-reply@skillshare.local>");
        mailer.send(user(), "https://app.example.com/reset-password?token=abc123", 30);

        server.verify();
    }

    @Test
    void send_noApiKeyConfigured_makesNoRequest() {
        // No expectations registered - MockRestServiceServer fails the
        // test immediately if any request comes in, so a passing
        // server.verify() here proves send() made no HTTP call at all.
        PasswordResetMailer mailer = new PasswordResetMailer(
                builder.build(), "", "SkillShare <no-reply@skillshare.local>");

        mailer.send(user(), "https://app.example.com/reset-password?token=abc123", 30);

        server.verify();
    }

    @Test
    void send_brevoReturnsError_isSwallowed() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withServerError());

        PasswordResetMailer mailer = new PasswordResetMailer(
                builder.build(), "test-api-key", "SkillShare <no-reply@skillshare.local>");

        mailer.send(user(), "https://app.example.com/reset-password?token=abc123", 30); // must not throw

        server.verify();
    }
}
