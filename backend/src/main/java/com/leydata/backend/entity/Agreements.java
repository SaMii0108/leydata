package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "agreements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Agreements {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_subject_id", insertable = false, updatable = false)
    private DataSubjects dataSubject;

    @Column(name = "data_subject_id", nullable = false)
    private UUID dataSubjectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", insertable = false, updatable = false)
    private Templates template;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "template_version", nullable = false)
    private Integer templateVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", insertable = false, updatable = false)
    private PrivacyDocuments document;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "status", nullable = false)
    private String status; // ACTIVE, REVOKED, EXPIRED

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_agreements_id", insertable = false, updatable = false)
    private Agreements previousAgreement;

    @Column(name = "previous_agreements_id")
    private UUID previousAgreementsId;

    @Column(name = "expiration")
    private LocalDateTime expiration;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "hash_sha256")
    private String hashSha256;

    @Column(name = "previous_hash")
    private String previousHash;

    @OneToMany(mappedBy = "agreement", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AgreementsPurposes> agreementsPurposes;

    @OneToMany(mappedBy = "agreement", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AgreementMetadata> agreementMetadata;

    @OneToMany(mappedBy = "agreement", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AgreementIntegrityLog> agreementIntegrityLogs;
}