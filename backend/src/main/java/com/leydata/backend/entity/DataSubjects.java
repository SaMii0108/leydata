package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "data_subjects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DataSubjects {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "identifier", nullable = false)
    private String identifier;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}