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
    private String tableName;

    @Column(name = "record_id")
    private UUID recordId;

    @Column(name = "action", nullable = false)
    private String action; // INSERT, UPDATE, DELETE

    @Column(name = "old_data", columnDefinition = "JSONB")
    private String oldData;

    @Column(name = "new_data", columnDefinition = "JSONB")
    private String newData;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "log_hash")
    private String logHash;

    @Column(name = "previous_log_hash")
    private String previousLogHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}