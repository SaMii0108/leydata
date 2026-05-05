package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agreements_purposes")
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

    @Column(name = "purpose_code")
    private String purposeCode;

    @Column(name = "purpose_name")
    private String purposeName;

    @Column(name = "purpose_description", columnDefinition = "TEXT")
    private String purposeDescription;

    @Column(name = "purpose_short_description")
    private String purposeShortDescription;

    @Column(name = "purpose_required")
    private Boolean purposeRequired;

    @Column(name = "purpose_revocable")
    private Boolean purposeRevocable;

    @Column(name = "purpose_hash")
    private String purposeHash;

    @Column(name = "legal_basis_code")
    private String legalBasisCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}