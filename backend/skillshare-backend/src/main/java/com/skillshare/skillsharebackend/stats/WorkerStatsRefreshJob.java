package com.skillshare.skillsharebackend.stats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic safety-net refresh of {@code mv_worker_stats}, on top of the
 * synchronous refreshes {@link WorkerStatsRefreshService}'s javadoc
 * documents. Exists so the view can't drift stale even from a write path
 * this project didn't think to hook (e.g. a booking status changed via
 * direct SQL, or a future code path that forgets to call the synchronous
 * refresh) — same "belt and suspenders" reasoning as
 * {@code NotificationDispatchJob} polling the outbox instead of relying
 * solely on trigger-time delivery.
 *
 * <p>Interval externalized as
 * {@code skillshare.worker-stats.refresh-interval-ms} (default 60s),
 * same convention as {@code NotificationDispatchJob}'s dispatch interval.
 * A failed refresh is logged and skipped, never left to kill the
 * scheduler thread — the next tick tries again.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkerStatsRefreshJob {

    private final WorkerStatsRefreshService workerStatsRefreshService;

    @Scheduled(fixedDelayString = "${skillshare.worker-stats.refresh-interval-ms:60000}")
    public void refreshPeriodically() {
        try {
            workerStatsRefreshService.refresh();
        } catch (Exception ex) {
            log.warn("WorkerStatsRefreshJob: refresh failed: {}", ex.getMessage());
        }
    }
}
