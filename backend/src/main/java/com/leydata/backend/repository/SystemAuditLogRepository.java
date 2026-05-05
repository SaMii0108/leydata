package com.leydata.backend.repository;

import com.leydata.backend.entity.SystemAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SystemAuditLogRepository extends JpaRepository<SystemAuditLog, UUID> {
}