package com.skillshare.skillsharebackend.domain.enums;

/** Mirrors the Postgres native enum type `notification_channel`. In-app
 *  is deliberately NOT a value here — see notification-system-design.md;
 *  the notifications row itself IS the in-app notification. Lowercase to
 *  match the Postgres labels exactly — see AccountStatus's javadoc for why. */
public enum NotificationChannel {
    email, sms
}
