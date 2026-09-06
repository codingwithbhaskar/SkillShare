package com.skillshare.skillsharebackend.notification;

import com.skillshare.skillsharebackend.domain.NotificationDelivery;
import com.skillshare.skillsharebackend.domain.enums.DeliveryStatus;
import com.skillshare.skillsharebackend.domain.enums.NotificationChannel;
import com.skillshare.skillsharebackend.repository.NotificationDeliveryProjection;
import com.skillshare.skillsharebackend.repository.NotificationDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Phase 5 - the outbox dispatch job: polls
 * {@code vw_pending_notification_deliveries} on a fixed delay, sends each
 * one through {@link NotificationSender}, and marks it {@code sent} or
 * {@code failed}. One delivery's failure is caught and recorded on that
 * row only - it never aborts the rest of the batch, and it never throws
 * out of {@link #dispatchPendingDeliveries}, so a bad delivery can't roll
 * back deliveries that already succeeded in the same tick.
 *
 * <p>Interval is externalized as
 * {@code skillshare.notification.dispatch-interval-ms} (default 30s) so
 * it can be tuned per environment without a code change; unset in
 * application.yml today, which is fine since the SpEL default covers it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchJob {

    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final NotificationSender notificationSender;

    @Scheduled(fixedDelayString = "${skillshare.notification.dispatch-interval-ms:30000}")
    @Transactional
    public void dispatchPendingDeliveries() {
        List<NotificationDeliveryProjection> pending = notificationDeliveryRepository.findPendingDeliveries();
        if (pending.isEmpty()) {
            return;
        }
        log.info("NotificationDispatchJob: {} pending deliveries", pending.size());
        for (NotificationDeliveryProjection delivery : pending) {
            dispatchOne(delivery);
        }
    }

    private void dispatchOne(NotificationDeliveryProjection delivery) {
        NotificationDelivery entity = notificationDeliveryRepository.findById(delivery.getDeliveryId())
                .orElse(null);
        if (entity == null) {
            // Raced away between the poll query and here (unlikely, but the
            // view and this lookup aren't in the same snapshot) - nothing to
            // update.
            return;
        }
        try {
            NotificationChannel channel = NotificationChannel.valueOf(delivery.getChannel());
            notificationSender.send(channel, delivery.getEmail(), delivery.getPhone(), delivery.getTitle(),
                    delivery.getMessage());
            entity.setStatus(DeliveryStatus.sent);
            entity.setSentAt(OffsetDateTime.now());
            entity.setAttemptCount(entity.getAttemptCount() + 1);
        } catch (Exception ex) {
            log.warn("NotificationDispatchJob: delivery {} failed: {}", delivery.getDeliveryId(), ex.getMessage());
            entity.setStatus(DeliveryStatus.failed);
            entity.setAttemptCount(entity.getAttemptCount() + 1);
            entity.setErrorMessage(ex.getMessage());
        }
        notificationDeliveryRepository.save(entity);
    }
}
