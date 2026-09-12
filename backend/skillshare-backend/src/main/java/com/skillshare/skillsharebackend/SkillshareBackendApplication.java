package com.skillshare.skillsharebackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @EnableScheduling} added in Phase 5 for
 *  {@code NotificationDispatchJob}'s {@code @Scheduled} outbox poll.
 *  {@code @EnableAsync} added for {@code PasswordResetMailer} - SMTP is a
 *  slow, sometimes-hanging external call (confirmed live 2026-09-12: an
 *  unconfigured/flaky SMTP endpoint froze the whole forgot-password
 *  request indefinitely before this), so sending runs off the request
 *  thread instead. */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class SkillshareBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillshareBackendApplication.class, args);
    }

}
