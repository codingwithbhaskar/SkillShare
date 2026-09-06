package com.skillshare.skillsharebackend.domain.enums;

/** Mirrors the Postgres native enum type `criteria_type` used by
 *  allocation_criteria.criteria_type. Lowercase to match the Postgres
 *  labels exactly — see AccountStatus's javadoc for why. */
public enum CriteriaType {
    normal, urgent
}
