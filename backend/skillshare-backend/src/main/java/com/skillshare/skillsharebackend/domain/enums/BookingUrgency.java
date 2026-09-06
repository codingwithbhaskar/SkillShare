package com.skillshare.skillsharebackend.domain.enums;

/** Mirrors the Postgres native enum type `booking_urgency`. Lowercase to
 *  match the Postgres labels exactly — see AccountStatus's javadoc for why. */
public enum BookingUrgency {
    normal, urgent
}
