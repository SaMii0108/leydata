package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "current_consent_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CurrentConsentState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Vínculo al usuario anonimizado
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pseudo_id", nullable = false)
    private PseudoMap pseudoMap;

    // Vínculo al permiso exacto (Ej: mkt-whatsapp-v2)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consent_config_id", nullable = false)
    private ConsentConfig consentConfig;

    // El switch actual: ¿Lo tiene prendido o apagado?
    @Column(name = "is_granted", nullable = false)
    private Boolean isGranted;

    // La fecha y hora de la uúltima actualización
    @Column(name = "last_updated_at", nullable = false)
    private LocalDateTime lastUpdatedAt;
}