package com.skillshare.skillsharebackend.web.dto;

/** Request body for {@code POST /api/auth/reset-password}. {@code token}
 *  is the plaintext token from the emailed reset link. */
public record ResetPasswordRequest(String token, String newPassword) {
}
