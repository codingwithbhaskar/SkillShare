package com.skillshare.skillsharebackend.notification;

import com.skillshare.skillsharebackend.domain.enums.NotificationChannel;

/**
 * Abstraction {@code NotificationDispatchJob} sends every pending outbox
 * delivery through. {@link LoggingNotificationSender} is the only
 * implementation for now - a deliberate, documented placeholder, per
 * notification-system-design.md's own honest caveat that real SMS needs a
 * paid/verified account and isn't realistic for arbitrary seed phone
 * numbers in a course project (real email is more feasible but still out
 * of Phase 5 scope). Swapping in a real Spring Mail/Twilio-backed
 * implementation later is a one-class change against this interface, not
 * a rewrite of the dispatch job itself.
 *
 * <p>Implementations signal failure by throwing - {@code
 * NotificationDispatchJob} catches it and marks that one delivery
 * {@code failed} without affecting the rest of the batch.
 */
public interface NotificationSender {
    void send(NotificationChannel channel, String recipientEmail, String recipientPhone, String title,
            String message);
}
