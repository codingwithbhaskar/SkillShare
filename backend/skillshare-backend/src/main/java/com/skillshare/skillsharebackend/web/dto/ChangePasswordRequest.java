package com.skillshare.skillsharebackend.web.dto;

/** Request body for {@code POST /api/users/me/change-password}. Requires
 *  proving knowledge of the current password - unlike the "forgot
 *  password" flow ({@code PasswordResetService}), which is specifically
 *  for when the caller does NOT know it. */
public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
