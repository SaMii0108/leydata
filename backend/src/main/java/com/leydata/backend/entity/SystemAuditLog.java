package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;
import java.time.LocalDateTime;
import java.util.UUID;

//Implementa Persistable<UUID> para controlar isNew():
//Spring Data JPA llama save() → isNew()=true → persist() (INSERT) en vez de merge() (UPDATE).
//Necesario porque asignamos el UUID manualmente antes de guardar (para incluirlo en el hash SHA-256).
@Entity
@Table(name = "system_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SystemAuditLog implements Persistable<UUID> {

    @Id
    private UUID id;

    //isNew() siempre retorna true: este objeto nunca es "detachado", siempre es nuevo.
    //Sin esto, Spring Data JPA ve id!=null → merge() → falla en fila inexistente.
    @Override
    @Transient
    public boolean isNew() {
        return true;
    }

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
    private String actorId; // keycloak_id (sub) del usuario que ejecutó la acción

    @Column(name = "actor_role", nullable = false)
    private String actorRole; // rol exacto al momento de la acción: "ADMIN", "DPO", "JEFE_DOMINIO"

    @Column(name = "ip_address")
    private String ipAddress; // IP real del operador (via X-Forwarded-For o remoteAddr)

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent; // navegador + SO del operador

    @Column(name = "request_id", length = 64)
    private String requestId; // X-Request-ID generado por WAF/NGINX — permite correlacionar con sus logs

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; // timestamp de la acción

    @Column(name = "log_hash", nullable = false)
    private String logHash; // hash SHA-256 de este registro

    @Column(name = "previous_log_hash")
    private String previousLogHash; // hash del registro anterior (null si es el primero)
}