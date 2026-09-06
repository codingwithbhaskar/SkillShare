package com.skillshare.skillsharebackend.domain.enums;

/** Mirrors the Postgres native enum type `delivery_status`. Lowercase to
 *  match the Postgres labels exactly — see AccountStatus's javadoc for why. */
public enum DeliveryStatus {
    pending, sent, failed
}
