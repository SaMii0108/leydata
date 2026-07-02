package com.leydata.backend.audit.infrastructure.persistence;

import com.leydata.backend.entity.DbTamperLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DbTamperLogRepository extends JpaRepository<DbTamperLog, UUID> {

    List<DbTamperLog> findByProcessedFalseOrderByDetectedAtAsc();
}
