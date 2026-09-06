package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByTableNameAndRecordId(String tableName, Long recordId);
}
