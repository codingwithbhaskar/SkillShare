package com.skillshare.skillsharebackend.domain.enums;

/** Mirrors the Postgres native enum type `payment_status`. Lowercase to
 *  match the Postgres labels exactly — see AccountStatus's javadoc for why. */
public enum PaymentStatus {
    pending, completed, failed, refunded
}
