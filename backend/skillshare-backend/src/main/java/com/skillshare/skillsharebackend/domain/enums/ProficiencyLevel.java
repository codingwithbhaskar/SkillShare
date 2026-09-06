package com.skillshare.skillsharebackend.domain.enums;

/** Mirrors the Postgres native enum type `proficiency_level`. Lowercase to
 *  match the Postgres labels exactly — see AccountStatus's javadoc for why. */
public enum ProficiencyLevel {
    beginner, intermediate, expert
}
