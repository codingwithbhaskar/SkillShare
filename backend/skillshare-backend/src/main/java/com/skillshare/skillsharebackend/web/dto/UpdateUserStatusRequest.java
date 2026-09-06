package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.enums.AccountStatus;

/** Body for {@code PATCH /api/admin/users/{id}/status} - the only field
 *  an admin can change about another user's account from this panel.
 *  Editing name/email/phone/role is deliberately out of scope: those are
 *  the account's own identity, not something an operator should silently
 *  rewrite from a management screen. */
public record UpdateUserStatusRequest(AccountStatus status) {}
