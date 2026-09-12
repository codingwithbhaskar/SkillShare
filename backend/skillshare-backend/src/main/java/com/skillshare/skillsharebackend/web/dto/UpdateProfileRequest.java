package com.skillshare.skillsharebackend.web.dto;

/** Request body for {@code PUT /api/users/me} - the universal "edit my
 *  profile" endpoint every role (customer/worker/admin) can call, unlike
 *  {@code PUT /api/workers/me} (worker-domain fields: bio, rate,
 *  location, skills, availability - see {@code UpdateWorkerProfileRequest}).
 *  Partial-update semantics, same convention as that DTO: a null field is
 *  left untouched, a non-null one (including {@code ""}) is applied.
 *  Email and role are deliberately not editable here - email is the
 *  account's login identity, and role changes are an admin-only concern
 *  with no self-service endpoint at all. */
public record UpdateProfileRequest(String fullName, String phone) {
}
