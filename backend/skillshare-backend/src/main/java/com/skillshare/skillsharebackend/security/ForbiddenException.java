package com.skillshare.skillsharebackend.security;

/**
 * Thrown when an authenticated caller is who they say they are (Spring
 * Security's job, fully done since Phase 7) but isn't entitled to the
 * specific resource they're asking for (fine-grained authorization - the
 * documented follow-up gap this class closes). Mapped to 403 by
 * {@code GlobalExceptionHandler}, distinct from the 403 Spring Security's
 * own access-denied handler produces for a missing role, but the same
 * status code from the client's point of view.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
