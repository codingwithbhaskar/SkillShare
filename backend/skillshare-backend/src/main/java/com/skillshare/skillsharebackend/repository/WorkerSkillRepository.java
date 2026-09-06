package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.WorkerSkill;
import com.skillshare.skillsharebackend.domain.ids.WorkerSkillId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkerSkillRepository extends JpaRepository<WorkerSkill, WorkerSkillId> {
    List<WorkerSkill> findByWorker_WorkerId(Long workerId);
    List<WorkerSkill> findBySkill_SkillId(Long skillId);

    /** Delete-guard for {@code AdminService.deleteSkill} - same
     *  rationale as {@code WorkerServiceRepository.existsByService_ServiceId}. */
    boolean existsBySkill_SkillId(Long skillId);
}
