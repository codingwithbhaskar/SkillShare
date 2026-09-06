package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.QueryBenchmarkResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QueryBenchmarkResultRepository extends JpaRepository<QueryBenchmarkResult, Long> {
    List<QueryBenchmarkResult> findByQueryLabel(String queryLabel);
}
