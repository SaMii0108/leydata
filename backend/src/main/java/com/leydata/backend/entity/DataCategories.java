package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "data_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    // true = definida por Ley 21.719, no se puede eliminar ni renombrar
    // false = creada por el DPO para su organización
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "dataCategory", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PurposeDataCategories> purposeDataCategories;
}