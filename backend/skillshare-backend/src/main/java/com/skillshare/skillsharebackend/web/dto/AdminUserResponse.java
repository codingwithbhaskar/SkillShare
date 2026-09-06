package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import com.skillshare.skillsharebackend.domain.enums.UserRole;

import java.time.OffsetDateTime;

/** Response shape for {@code GET /api/admin/users} and the status-update
 *  endpoint - the admin "manage users" view. Includes every account
 *  (customer/worker/admin) as one flat list; the frontend filters/badges
 *  by role rather than the backend exposing three separate endpoints. */
public record AdminUserResponse(
        Long userId,
        UserRole role,
        String fullName,
        String email,
        String phone,
        AccountStatus status,
        OffsetDateTime createdAt) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getUserId(),
                user.getRole(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                user.getCreatedAt());
    }
}
