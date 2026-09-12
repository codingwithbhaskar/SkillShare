package com.skillshare.skillsharebackend.auth;

import com.skillshare.skillsharebackend.domain.PasswordResetToken;
import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import com.skillshare.skillsharebackend.domain.enums.UserRole;
import com.skillshare.skillsharebackend.repository.PasswordResetTokenRepository;
import com.skillshare.skillsharebackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PasswordResetService}. Repositories, the
 * {@link PasswordEncoder} and {@link PasswordResetMailer} are all mocked -
 * these exercise the service's own branching, the "never reveal whether
 * an email exists" contract, and the token hash/expiry/single-use rules.
 * Email content is covered separately by {@link PasswordResetMailerTest}.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private UserRepository userRepository;
    private PasswordResetTokenRepository tokenRepository;
    private PasswordEncoder passwordEncoder;
    private PasswordResetMailer mailer;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        tokenRepository = Mockito.mock(PasswordResetTokenRepository.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        mailer = Mockito.mock(PasswordResetMailer.class);
        service = new PasswordResetService(
                userRepository, tokenRepository, passwordEncoder, mailer, "https://app.example.com/");
    }

    private User user(long id, AccountStatus status) {
        return User.builder()
                .userId(id).role(UserRole.customer).fullName("Asha Patil")
                .email("asha@example.com").passwordHash("old-hash").status(status)
                .build();
    }

    private static String sha256Hex(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    // ---- requestReset ------------------------------------------------------

    @Test
    void requestReset_unknownEmail_savesNothing_mailsNothing() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.requestReset("nobody@example.com");

        verify(tokenRepository, never()).save(any());
        verify(mailer, never()).send(any(), any(), anyLong());
    }

    @Test
    void requestReset_suspendedAccount_savesNothing_mailsNothing() {
        when(userRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(user(1L, AccountStatus.suspended)));

        service.requestReset("asha@example.com");

        verify(tokenRepository, never()).save(any());
        verify(mailer, never()).send(any(), any(), anyLong());
    }

    @Test
    void requestReset_activeAccount_storesHashedToken_andAsksMailerToSendPlaintextLink() throws Exception {
        User user = user(1L, AccountStatus.active);
        when(userRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(user));

        service.requestReset("  asha@example.com  "); // also checks trimming

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken saved = tokenCaptor.getValue();

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailer).send(eq(user), urlCaptor.capture(), eq(30L));
        String resetUrl = urlCaptor.getValue();

        assertTrue(resetUrl.startsWith("https://app.example.com/reset-password?token="));
        String rawToken = resetUrl.substring(resetUrl.indexOf("token=") + "token=".length());

        // What's stored is the hash of what's emailed, never the raw token.
        assertEquals(sha256Hex(rawToken), saved.getTokenHash());
        assertNotEquals(saved.getTokenHash(), rawToken);
        assertTrue(saved.getExpiresAt().isAfter(OffsetDateTime.now().plusMinutes(25)));
    }

    // ---- resetPassword ---------------------------------------------------

    private PasswordResetToken token(User user, OffsetDateTime expiresAt, OffsetDateTime usedAt) {
        return PasswordResetToken.builder()
                .tokenId(10L).user(user).tokenHash("hash")
                .expiresAt(expiresAt).usedAt(usedAt).build();
    }

    @Test
    void resetPassword_validToken_reencodesPassword_andStampsUsedAt() throws Exception {
        User user = user(1L, AccountStatus.active);
        when(tokenRepository.findByTokenHash(sha256Hex("raw-token")))
                .thenReturn(Optional.of(token(user, OffsetDateTime.now().plusMinutes(10), null)));
        when(passwordEncoder.encode("newpass123")).thenReturn("new-hash");

        service.resetPassword("raw-token", "newpass123");

        assertEquals("new-hash", user.getPasswordHash());
        verify(userRepository).save(user);

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        assertNotNull(captor.getValue().getUsedAt());
    }

    @Test
    void resetPassword_unknownToken_throws() throws Exception {
        when(tokenRepository.findByTokenHash(sha256Hex("raw-token"))).thenReturn(Optional.empty());

        assertThrows(InvalidResetTokenException.class, () -> service.resetPassword("raw-token", "newpass123"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_expiredToken_throws() throws Exception {
        User user = user(1L, AccountStatus.active);
        when(tokenRepository.findByTokenHash(sha256Hex("raw-token")))
                .thenReturn(Optional.of(token(user, OffsetDateTime.now().minusMinutes(1), null)));

        assertThrows(InvalidResetTokenException.class, () -> service.resetPassword("raw-token", "newpass123"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_alreadyUsedToken_throws() throws Exception {
        User user = user(1L, AccountStatus.active);
        when(tokenRepository.findByTokenHash(sha256Hex("raw-token")))
                .thenReturn(Optional.of(token(user, OffsetDateTime.now().plusMinutes(10), OffsetDateTime.now().minusMinutes(2))));

        assertThrows(InvalidResetTokenException.class, () -> service.resetPassword("raw-token", "newpass123"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_shortPassword_throwsValidation() {
        assertThrows(AuthValidationException.class, () -> service.resetPassword("raw-token", "short"));
        verify(tokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void resetPassword_blankToken_throws() {
        assertThrows(InvalidResetTokenException.class, () -> service.resetPassword("  ", "newpass123"));
    }
}
