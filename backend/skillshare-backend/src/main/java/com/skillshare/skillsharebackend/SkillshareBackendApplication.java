package com.skillshare.skillsharebackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @EnableScheduling} added in Phase 5 for
 *  {@code NotificationDispatchJob}'s {@code @Scheduled} outbox poll -
 *  nothing before Phase 5 used Spring's scheduling support. */
@SpringBootApplication
@EnableScheduling
public class SkillshareBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillshareBackendApplication.class, args);
    }

}
