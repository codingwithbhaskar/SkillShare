package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.WorkerAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkerAvailabilityRepository extends JpaRepository<WorkerAvailability, Long> {
    List<WorkerAvailability> findByWorker_WorkerId(Long workerId);
    List<WorkerAvailability> findByWorker_WorkerIdAndDayOfWeek(Long workerId, Short dayOfWeek);
}
