package com.skillshare.skillsharebackend.auth;

/** Thrown by {@link AuthService#register} when the requested email is
 *  already registered. Mapped to HTTP 409 by
 *  {@code GlobalExceptionHandler}. */
public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}
