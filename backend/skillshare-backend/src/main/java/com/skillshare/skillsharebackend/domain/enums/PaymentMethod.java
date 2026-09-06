package com.skillshare.skillsharebackend.domain.enums;

/** Mirrors the Postgres native enum type `payment_method`. Lowercase to
 *  match the Postgres labels exactly — see AccountStatus's javadoc for why. */
public enum PaymentMethod {
    cash, card, upi, wallet
}
