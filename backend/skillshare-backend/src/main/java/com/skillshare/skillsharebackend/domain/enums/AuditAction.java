package com.skillshare.skillsharebackend.domain.enums;

/** Mirrors the Postgres native enum type `audit_action`. audit_log rows
 *  are written exclusively by the generic DB trigger (fn_audit_trigger) —
 *  this enum exists so the app can READ audit history, not write it. */
public enum AuditAction {
    INSERT, UPDATE, DELETE
}
