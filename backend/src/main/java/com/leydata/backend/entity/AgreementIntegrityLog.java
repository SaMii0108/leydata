package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agreement_integrity_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgreementIntegrityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agreement_id", insertable = false, updatable = false)
    private Agreements agreement;

    @Column(name = "agreement_id", nullable = false)
    private UUID agreementId;

    @Column(name = "expected_hash", nullable = false)
    private String expectedHash;

    @Column(name = "actual_hash", nullable = false)
    private String actualHash;

    @Column(name = "detected_by")
    private String detectedBy;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;
}