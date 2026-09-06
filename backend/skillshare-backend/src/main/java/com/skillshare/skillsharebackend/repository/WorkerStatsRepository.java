package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.WorkerStats;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerStatsRepository extends JpaRepository<WorkerStats, Long> {
}
