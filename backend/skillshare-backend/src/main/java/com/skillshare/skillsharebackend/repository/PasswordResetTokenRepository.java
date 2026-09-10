package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /** Lookup is always by the hash of the token from the reset link,
     *  never by user — the caller presents the token, not an identity. */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
