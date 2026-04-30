package com.leydata.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "consent_domain")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class ConsentDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_name", nullable = false)
    private String categoryName; // "Marketing", "Legal", "Analysis", etc

    // Es unico para evitar dominios con el mismo nombre tecnico
    @Column(name = "domain_slug", nullable = false, unique = true)
    private String domainSlug; // "mkt", "legal", "stats"

    // Descripcion tecnica del dominio
    @Column(name = "technical_description", columnDefinition = "TEXT")
    private String technicalDescription;
}
