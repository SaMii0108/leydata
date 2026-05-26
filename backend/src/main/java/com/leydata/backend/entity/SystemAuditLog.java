package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "system_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SystemAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "table_name", nullable = false)
    private String tableName; // "users", "domains", "purpose_requests"

    @Column(name = "record_id")
    private UUID recordId; // ID del registro afectado

    @Column(name = "action", nullable = false)
    private String action; // "CREAR", "DESACTIVAR", "BLOQUEAR", "CAMBIAR_ROL", "APROBAR",
                           // "RECHAZAR","SOLICITAR",etc

    @Column(name = "old_data", columnDefinition = "TEXT")
    private String oldData; // estado anterior

    @Column(name = "new_data", columnDefinition = "TEXT")
    private String newData; // estado nuevo

    @Column(name = "actor_id")
    private UUID actorId; // ID del usuario que ejecutó la acción

    @Column(name = "actor_role", nullable = false)
    private String actorRole; // rol exacto al momento de la acción: "ADMIN", "DPO", "JEFE_DOMINIO"

    @Column(name = "ip_address")
    private String ipAddress; // IP real del operador (via X-Forwarded-For o remoteAddr)

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent; // navegador + SO del operador

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; // timestamp de la acción

    @Column(name = "log_hash", nullable = false)
    private String logHash; // hash SHA-256 de este registro

    @Column(name = "previous_log_hash")
    private String previousLogHash; // hash del registro anterior (null si es el primero)
}