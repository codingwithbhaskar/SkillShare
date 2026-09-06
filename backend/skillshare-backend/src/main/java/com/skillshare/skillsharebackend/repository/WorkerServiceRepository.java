package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.WorkerService;
import com.skillshare.skillsharebackend.domain.ids.WorkerServiceId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkerServiceRepository extends JpaRepository<WorkerService, WorkerServiceId> {
    List<WorkerService> findByWorker_WorkerId(Long workerId);
    List<WorkerService> findByService_ServiceId(Long serviceId);

    /** Delete-guard for {@code AdminService.deleteService} - a service
     *  still offered by at least one worker can't be removed (FK is
     *  ON DELETE CASCADE at the DB level for this join table specifically,
     *  but silently cascading a worker's own rate rows away from an admin
     *  "delete service" click would be a surprising side effect, so this
     *  is blocked at the service layer regardless of what the FK itself
     *  would allow). */
    boolean existsByService_ServiceId(Long serviceId);
}
