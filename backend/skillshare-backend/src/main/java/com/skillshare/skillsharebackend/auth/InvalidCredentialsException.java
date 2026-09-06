package com.skillshare.skillsharebackend.auth;

/** Thrown by {@link AuthService#login} when the email doesn't exist or
 *  the password doesn't match. Deliberately uses the same message for
 *  both cases, so the response never reveals whether a given email is
 *  registered. Mapped to HTTP 401 by {@code GlobalExceptionHandler}. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
