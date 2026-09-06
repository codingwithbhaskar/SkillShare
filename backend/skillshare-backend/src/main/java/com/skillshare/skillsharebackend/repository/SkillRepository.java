package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {
    Optional<Skill> findBySkillName(String skillName);

    /** Same rationale as {@code ServiceRepository.findByServiceNameAndServiceIdNot}. */
    Optional<Skill> findBySkillNameAndSkillIdNot(String skillName, Long skillId);
}
