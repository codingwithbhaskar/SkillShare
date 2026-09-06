package com.skillshare.skillsharebackend.domain.enums;

/**
 * Mirrors the Postgres native enum type `account_status`. Used by both
 * users.status and workers.status.
 *
 * Constant names are deliberately lowercase, matching the Postgres enum's
 * labels exactly ('active', 'inactive', 'suspended') — Hibernate's
 * {@code @JdbcTypeCode(SqlTypes.NAMED_ENUM)} maps a Java enum to a native
 * Postgres enum column by name (Enum.valueOf on read, Enum.name() on
 * write), so any case mismatch fails at runtime with "No enum constant
 * ...active" rather than at compile time or schema-validation time —
 * ddl-auto: validate only checks the column's TYPE, not that the labels
 * match a Java enum's constant names, so this was invisible until Phase 3
 * actually read a row back into a Worker entity. Departs from the usual
 * Java UPPER_SNAKE_CASE convention on purpose; that's the trade-off for
 * this mapping strategy.
 */
public enum AccountStatus {
    active, inactive, suspended
}
