package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_report")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Un nombre para identificar la revisión
    @Column(name = "report_name", nullable = false)
    private String reportName; // Ejemplo: "Auditoría Mensual - Abril 2026"

    // Cuándo el Agente de Python terminó de correr su proceso de validación de
    // firmas.
    @Column(name = "verification_date", nullable = false)
    private LocalDateTime verificationDate;

    // Un validaor
    @Column(name = "is_integrity_validated", nullable = false)
    private Boolean isIntegrityValidated;

    // Si la integridad falló, el Agente escribe aquí qué pasó
    @Column(name = "detected_anomalies", columnDefinition = "TEXT")
    private String detectedAnomalies;

    // Cuántas filas revisó el Agente. Da una idea del volumen de información
    // protegida.
    @Column(name = "total_records_scanned", nullable = false)
    private Long totalRecordsScanned;
}