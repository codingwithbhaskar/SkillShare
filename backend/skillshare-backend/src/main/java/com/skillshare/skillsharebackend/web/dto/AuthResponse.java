package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.enums.UserRole;

/** Response for both {@code /api/auth/register} and {@code /api/auth/
 *  login} - the JWT bearer token plus enough user info for the frontend
 *  to render role-based UI without a second round-trip. */
public record AuthResponse(
        String token,
        Long userId,
        UserRole role,
        String fullName,
        String email,
        // Added alongside AuthService.updateProfile - that endpoint reuses
        // this DTO (a fresh token + current fields) as its response, so
        // the frontend can update its stored auth object without a
        // separate GET or forcing a re-login.
        String phone) {
}
