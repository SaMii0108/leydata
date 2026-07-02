package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "entity_integrity_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EntityIntegrityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_type", nullable = false)
    private String entityType; // AGREEMENT, PURPOSE, TEMPLATE, DOCUMENT

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "stored_hash", nullable = false)
    private String storedHash;

    @Column(name = "recalculated_hash", nullable = false)
    private String recalculatedHash;

    @Column(name = "is_valid", nullable = false)
    private Boolean isValid;

    @Column(name = "check_type", nullable = false)
    private String checkType; // SCHEDULED, MANUAL, ON_DEMAND

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "hash_sha256", unique = true)
    private String hashSha256;

    @Column(name = "previous_hash_sha256_id")
    private String previousHashSha256Id;
}
