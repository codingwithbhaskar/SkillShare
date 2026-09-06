package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.AllocationCriteria;
import com.skillshare.skillsharebackend.domain.enums.CriteriaType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AllocationCriteriaRepository extends JpaRepository<AllocationCriteria, Long> {
    Optional<AllocationCriteria> findByCriteriaType(CriteriaType criteriaType);
}
