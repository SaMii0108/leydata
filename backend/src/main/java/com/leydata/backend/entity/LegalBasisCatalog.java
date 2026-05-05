package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "legal_basis_catalog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LegalBasisCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "consent_required", nullable = false)
    private Boolean consentRequired;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}