package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "data_retention_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DataRetentionPolicies {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purpose_data_category_id", insertable = false, updatable = false)
    private PurposeDataCategories purposeDataCategory;

    @Column(name = "purpose_data_category_id", nullable = false, unique = true)
    private UUID purposeDataCategoryId;

    @Column(name = "retention_period", nullable = false)
    private Integer retentionPeriod;

    @Column(name = "retention_unit", nullable = false)
    private String retentionUnit;

    @Column(name = "legal_justification", columnDefinition = "TEXT")
    private String legalJustification;

    @Column(name = "anonymize_after", nullable = false)
    private Boolean anonymizeAfter;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}