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
@Table(name = "purposes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Purposes {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "short_description")
    private String shortDescription;

    @Column(name = "required", nullable = false)
    private Boolean required;

    @Column(name = "revocable", nullable = false)
    private Boolean revocable;

    @Column(name = "presentation_order")
    private Integer presentationOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_basis_id", insertable = false, updatable = false)
    private LegalBasisCatalog legalBasis;

    @Column(name = "legal_basis_id")
    private UUID legalBasisId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id", insertable = false, updatable = false)
    private Domains domain;

    @Column(name = "domain_id")
    private UUID domainId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private Users createdByUser;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by", insertable = false, updatable = false)
    private Users approvedByUser;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "hash_sha256")
    private String hashSha256;

    @OneToMany(mappedBy = "purpose", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DocumentPurposes> documentPurposes;

    @OneToMany(mappedBy = "purpose", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PurposeDataCategories> purposeDataCategories;

    @OneToMany(mappedBy = "purpose", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TemplatePurposes> templatePurposes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purpose_request_id", insertable = false, updatable = false)
    private PurposeRequests purposeRequest;

    @Column(name = "purpose_request_id")
    private UUID purposeRequestId;
}
