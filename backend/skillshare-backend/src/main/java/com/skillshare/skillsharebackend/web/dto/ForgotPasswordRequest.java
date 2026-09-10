package com.skillshare.skillsharebackend.web.dto;

/** Request body for {@code POST /api/auth/forgot-password}. */
public record ForgotPasswordRequest(String email) {
}
