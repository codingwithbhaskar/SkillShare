package com.skillshare.skillsharebackend.admin;

/** Bad input or a state conflict on an admin-management action - a
 *  blank/duplicate service or skill name, an attempt to suspend the
 *  caller's own admin account, or a delete blocked because the row is
 *  still referenced elsewhere. Mapped to 400 by GlobalExceptionHandler,
 *  same convention as {@code WorkerProfileValidationException}. */
public class AdminValidationException extends RuntimeException {
    public AdminValidationException(String message) {
        super(message);
    }
}
