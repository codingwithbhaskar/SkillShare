package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.enums.UserRole;

/** Request body for {@code POST /api/auth/register}. {@code role}
 *  selects between {@code admin}/{@code customer}/{@code worker} -
 *  registering as {@code worker} also creates a bare {@code workers}
 *  row (see {@code AuthService.register}'s javadoc for what "bare"
 *  means and what's still missing). */
public record RegisterRequest(
        UserRole role,
        String fullName,
        String email,
        String phone,
        String password) {
}
