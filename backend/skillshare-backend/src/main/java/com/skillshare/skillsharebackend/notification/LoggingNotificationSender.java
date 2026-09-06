package com.skillshare.skillsharebackend.notification;

import com.skillshare.skillsharebackend.domain.enums.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Default {@link NotificationSender} - logs what it would have sent and
 *  nothing else. See that interface's javadoc for why this is the right
 *  Phase 5 scope. */
@Component
@Slf4j
public class LoggingNotificationSender implements NotificationSender {

    @Override
    public void send(NotificationChannel channel, String recipientEmail, String recipientPhone, String title,
            String message) {
        String recipient = channel == NotificationChannel.sms ? recipientPhone : recipientEmail;
        log.info("[NOTIFICATION-DISPATCH] would send {} to {}: {} - {}", channel, recipient, title, message);
    }
}
