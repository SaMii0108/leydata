package com.leydata.backend.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leydata.backend.entity.SystemAuditLog;

import java.util.UUID;

public interface SystemAuditLogRepository extends JpaRepository<SystemAuditLog, UUID> {
}