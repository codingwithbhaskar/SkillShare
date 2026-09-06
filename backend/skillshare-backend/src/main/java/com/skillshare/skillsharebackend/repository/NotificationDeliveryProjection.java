package com.skillshare.skillsharebackend.repository;

/** Projection for {@code vw_pending_notification_deliveries}
 *  (03_views_v3.sql) - exactly what {@code NotificationDispatchJob}
 *  polls. {@code channel} comes back as {@code String}, not
 *  {@code NotificationChannel}: the wrapping native query casts it with
 *  {@code channel::text}, the same defensive pattern
 *  {@code AllocationService} uses for {@code status::text} - see that
 *  class's javadoc for why an un-cast native enum column read through
 *  plain JDBC/Spring Data isn't safe to trust across driver versions
 *  (the exact bug Phase 3 found). */
public interface NotificationDeliveryProjection {
    Long getDeliveryId();
    Long getNotificationId();
    String getChannel();
    Integer getAttemptCount();
    String getType();
    String getTitle();
    String getMessage();
    Long getUserId();
    String getEmail();
    String getPhone();
}
