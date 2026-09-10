package com.skillshare.skillsharebackend.auth;

/** Thrown by {@link PasswordResetService#resetPassword} when the reset
 *  token is missing, unknown, already used, or expired. Deliberately
 *  vague messages — they never distinguish "no such token" from "expired"
 *  in a way that would help an attacker. Mapped to HTTP 400 by
 *  {@code GlobalExceptionHandler}. */
public class InvalidResetTokenException extends RuntimeException {
    public InvalidResetTokenException(String message) {
        super(message);
    }
}
