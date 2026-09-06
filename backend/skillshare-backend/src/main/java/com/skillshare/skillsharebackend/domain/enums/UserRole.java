package com.skillshare.skillsharebackend.domain.enums;

/** Mirrors the Postgres native enum type `user_role` (01_schema_v3.sql).
 *  Lowercase to match the Postgres labels exactly — see AccountStatus's
 *  javadoc for why. */
public enum UserRole {
    admin, customer, worker
}
