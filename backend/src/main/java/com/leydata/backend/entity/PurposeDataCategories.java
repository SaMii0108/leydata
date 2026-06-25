package com.leydata.backend.entity;

import com.leydata.backend.purposedatacategory.domain.enums.DataUseType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.HashSet;
import java.util.Set;
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

    @ElementCollection(targetClass = DataUseType.class, fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
        name = "purpose_data_category_data_uses",
        joinColumns = @JoinColumn(name = "purpose_data_category_id")
    )
    @Column(name = "data_use")
    private Set<DataUseType> dataUses = new HashSet<>();
}