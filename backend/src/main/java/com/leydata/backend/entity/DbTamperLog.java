package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "db_tamper_log")
@Getter
@Setter
@NoArgsConstructor
public class DbTamperLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "table_name", nullable = false)
    private String tableName;

    @Column(name = "operation", nullable = false)
    private String operation;   // UPDATE | DELETE

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Column(name = "old_data", columnDefinition = "TEXT")
    private String oldData;

    @Column(name = "new_data", columnDefinition = "TEXT")
    private String newData;

    @Column(name = "db_user", nullable = false)
    private String dbUser;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private LocalDateTime detectedAt;

    @Column(name = "processed", nullable = false)
    private boolean processed;
}
