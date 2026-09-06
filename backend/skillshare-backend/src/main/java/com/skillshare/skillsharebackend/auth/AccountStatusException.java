package com.skillshare.skillsharebackend.auth;

/** Thrown by {@link AuthService#login} when the credentials are correct
 *  but the account's {@code status} isn't {@code active} (suspended by
 *  an admin, or self-deactivated via {@link AuthService#deactivateSelf}).
 *  Deliberately a distinct exception/status from
 *  {@link InvalidCredentialsException} - unlike a wrong password, there's
 *  no information-leak concern in telling the user their own account is
 *  suspended/deactivated, and the frontend needs to tell the two cases
 *  apart to show a useful message. Mapped to HTTP 403 by
 *  {@code GlobalExceptionHandler}. */
public class AccountStatusException extends RuntimeException {
    public AccountStatusException(String message) {
        super(message);
    }
}
