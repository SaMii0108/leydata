package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pseudo_map")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PseudoMap {

    @Id
    @Column(name = "pseudo_id", nullable = false, updatable = false)
    private String pseudoId; // Este es el Hash SHA-256 del usuario (NUNCA el gmail real)

    @Column(name = "source_system")
    private String sourceSystem; // Para saber de dónde viene. Ej: "Huerto Hogar App" o "CRM"

    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt;

    // Si el usuario pide que borren sus datos, se llena esta fecha y se rompe el
    // vínculo.
    @Column(name = "anonymized_at")
    private LocalDateTime anonymizedAt;
}