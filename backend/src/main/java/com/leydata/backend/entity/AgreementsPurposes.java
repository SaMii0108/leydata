package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agreements_purposes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"agreement_id", "purpose_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgreementsPurposes {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agreement_id", insertable = false, updatable = false)
    private Agreements agreement;

    @Column(name = "agreement_id", nullable = false)
    private UUID agreementId;

    @Column(name = "purpose_id", nullable = false)
    private UUID purposeId;

    @Column(name = "accepted", nullable = false)
    private Boolean accepted;

    @Column(name = "purpose_code", nullable = false)
    private String purposeCode;

    @Column(name = "purpose_name", nullable = false)
    private String purposeName;

    @Column(name = "purpose_description", columnDefinition = "TEXT", nullable = false)
    private String purposeDescription;

    @Column(name = "purpose_short_description", nullable = false)
    private String purposeShortDescription;

    @Column(name = "purpose_required", nullable = false)
    private Boolean purposeRequired;

    @Column(name = "purpose_revocable", nullable = false)
    private Boolean purposeRevocable;

    @Column(name = "purpose_hash")
    private String purposeHash;

    @Column(name = "legal_basis_code", nullable = false)
    private String legalBasisCode;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "status", nullable = false)
    private String status; // ACTIVE, EXPIRED, REVOKED

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "hash_sha256", unique = true)
    private String hashSha256;

    @Column(name = "previous_hash_sha256")
    private String previousHashSha256;
}