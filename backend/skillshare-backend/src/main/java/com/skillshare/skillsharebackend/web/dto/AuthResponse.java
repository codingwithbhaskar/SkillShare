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
        String email) {
}
