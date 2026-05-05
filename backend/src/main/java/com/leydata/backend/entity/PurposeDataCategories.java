package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "purpose_data_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurposeDataCategories {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purpose_id", insertable = false, updatable = false)
    private Purposes purpose;

    @Column(name = "purpose_id", nullable = false)
    private UUID purposeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_category_id", insertable = false, updatable = false)
    private DataCategories dataCategory;

    @Column(name = "data_category_id", nullable = false)
    private UUID dataCategoryId;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "purposeDataCategory")
    private DataRetentionPolicies dataRetentionPolicy;

    @Column(name = "required", nullable = false)
    private Boolean required;
}