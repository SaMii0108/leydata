package com.leydata.backend.audit.infrastructure.persistence;

import com.leydata.backend.entity.SystemAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SystemAuditLogRepository extends JpaRepository<SystemAuditLog, UUID> {

    // Lock de transacción PostgreSQL — serializa escrituras al ledger de auditoría.
    // pg_advisory_xact_lock bloquea hasta que la transacción activa libere el lock (commit/rollback).
    // La constante 7719 identifica este lock globalmente (número arbitrario, único por propósito).
    @Query(value = "SELECT pg_advisory_xact_lock(7719)", nativeQuery = true)
    void acquireAuditChainLock();

    // Obtener el registro más reciente para encadenar su hash al siguiente log
    Optional<SystemAuditLog> findTopByOrderByCreatedAtDesc();

    // Obtener todos los registros en orden ascendente para verificar la cadena completa
    List<SystemAuditLog> findAllByOrderByCreatedAtAsc();

    // Filtrar por keycloak_id del actor (operador que ejecutó la acción)
    Page<SystemAuditLog> findByActorIdOrderByCreatedAtDesc(String actorId, Pageable pageable);

    // Filtrar por tipo de acción: "CREAR_USUARIO", "BLOQUEAR_USUARIO", etc.
    Page<SystemAuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    // Filtrar por tabla afectada: "users", "domains", "purpose_requests"
    Page<SystemAuditLog> findByTableNameOrderByCreatedAtDesc(String tableName, Pageable pageable);

    // Todos los logs paginados, más recientes primero
    Page<SystemAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
