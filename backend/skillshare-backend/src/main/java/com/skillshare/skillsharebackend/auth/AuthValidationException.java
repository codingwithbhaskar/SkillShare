package com.skillshare.skillsharebackend.auth;

/** Thrown by {@link AuthService#register} for request-level validation
 *  failures (blank/malformed email, too-short password, blank full name,
 *  missing role). Mapped to HTTP 400 by {@code GlobalExceptionHandler}.
 *  This project doesn't use jakarta-validation annotations on DTOs
 *  anywhere (see {@code CreateBookingRequest} etc.) - validation is done
 *  defensively in the service layer throughout, and this follows the
 *  same convention rather than introducing a new one just for auth. */
public class AuthValidationException extends RuntimeException {
    public AuthValidationException(String message) {
        super(message);
    }
}
