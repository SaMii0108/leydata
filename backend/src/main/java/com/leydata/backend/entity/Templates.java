package com.leydata.backend.entity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "templates")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Templates {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_key", nullable = false)
    private String templateKey;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "title")
    private String title;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "change_reason", columnDefinition = "TEXT")
    private String changeReason;

    // Keycloak ID (sub del JWT) del usuario que creó el template
    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // Keycloak ID del DPO que aprobó el template
    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "activation_date")
    private OffsetDateTime activationDate;

    @Column(name = "hash_sha256", unique = true)
    private String hashSha256;

    @Column(name = "previous_hash_sha256")
    private String previousHashSha256;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TemplatePurposes> templatePurposes;
}
