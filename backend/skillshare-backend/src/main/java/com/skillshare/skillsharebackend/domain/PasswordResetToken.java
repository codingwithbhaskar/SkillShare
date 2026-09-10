package com.skillshare.skillsharebackend.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Maps to `password_reset_tokens` (V7). One row per password-reset
 * request. {@code tokenHash} is the SHA-256 (hex) of the random token
 * emailed to the user — the plaintext token is never persisted, so this
 * table leaking can't be turned into a password reset. A row is
 * single-use ({@code usedAt}) and time-limited ({@code expiresAt}); see
 * {@link com.skillshare.skillsharebackend.auth.PasswordResetService}.
 *
 * created_at is owned by the database (DEFAULT now()) — insertable=false,
 * updatable=false, same convention as {@link User}.
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long tokenId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
