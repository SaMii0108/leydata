package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "consent_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConsentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id", nullable = false)
    private ConsentDomain domain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private PrivacyDocument document;

    @Column(name = "technical_slug", nullable = false, unique = true)
    private String technicalSlug;

    @Column(name = "short_description", nullable = false)
    private String shortDescription; // Ej: "Acepto recibir promociones por WhatsApp"

    @Column(name = "long_description", columnDefinition = "TEXT", nullable = false)
    private String longDescription; // Ej: "Al marcar esta casilla, autoriza a la empresa a tratar sus datos de
                                    // contacto para enviarle ofertas mediante WhatsApp. Puede revocar esto en
                                    // cualquier momento..."

    @Column(name = "is_mandatory", nullable = false)
    private Boolean isMandatory;

    @Column(name = "config_status", nullable = false)
    private String configStatus;
}