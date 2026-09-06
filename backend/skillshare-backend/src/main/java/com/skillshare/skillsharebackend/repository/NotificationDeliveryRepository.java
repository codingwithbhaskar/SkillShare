package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.NotificationDelivery;
import com.skillshare.skillsharebackend.domain.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
    List<NotificationDelivery> findByStatus(DeliveryStatus status);

    /**
     * Wraps {@code vw_pending_notification_deliveries} (03_views_v3.sql) -
     * exactly what {@code NotificationDispatchJob} polls each tick,
     * pre-joined with recipient email/phone so it needs no second query.
     * See {@link NotificationDeliveryProjection}'s javadoc for why
     * {@code channel} is cast to text in the query.
     */
    @Query(value = """
            SELECT delivery_id, notification_id, channel::text AS channel, attempt_count,
                   type, title, message, user_id, email, phone
            FROM vw_pending_notification_deliveries
            """, nativeQuery = true)
    List<NotificationDeliveryProjection> findPendingDeliveries();
}
