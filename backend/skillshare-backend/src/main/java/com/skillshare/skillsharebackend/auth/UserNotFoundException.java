package com.skillshare.skillsharebackend.auth;

/** Thrown by {@link AuthService#deactivateSelf} if the JWT's subject
 *  userId no longer corresponds to a real {@code users} row - an edge
 *  case that should be essentially unreachable in practice (it would
 *  require the account to be deleted between token issuance and use;
 *  this app has no account-deletion feature), but handled explicitly
 *  rather than surfacing a raw 500. Mapped to HTTP 404 by
 *  {@code GlobalExceptionHandler}. */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
