package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "data_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DataCategories {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_sensitive", nullable = false)
    private Boolean isSensitive;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @OneToMany(mappedBy = "dataCategory", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.List<PurposeDataCategories> purposeDataCategories;
}