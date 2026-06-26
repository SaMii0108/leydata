package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purpose_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurposeRequests {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id", insertable = false, updatable = false)
    private Domains domain;

    @Column(name = "domain_id", nullable = false)
    private UUID domainId;

    @Column(name = "requester_id", nullable = false)
    private String requesterId;

    @Column(name = "requester_name")
    private String requesterName;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "justification", columnDefinition = "TEXT", nullable = false)
    private String justification;

    @Column(name = "requested_data", columnDefinition = "TEXT")
    private String requestedData; // JSON stored as TEXT (no JSONB in DB)

    @Column(name = "status", nullable = false)
    private String status; // PENDING, APPROVED, REJECTED

    @Column(name = "reviewer_id")
    private String reviewerId;

    @Column(name = "reviewer_name")
    private String reviewerName;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "purposeRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Purposes> purposes;
}