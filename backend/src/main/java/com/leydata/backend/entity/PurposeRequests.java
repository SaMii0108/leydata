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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", insertable = false, updatable = false)
    private Users requester;

    @Column(name = "domain_id", nullable = false)
    private UUID domainId;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "justification", columnDefinition = "TEXT", nullable = false)
    private String justification;

    @Column(name = "requested_data", columnDefinition = "JSONB")
    private String requestedData; // JSONB for requested data

    @Column(name = "status", nullable = false)
    private String status; // PENDING, APPROVED, REJECTED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", insertable = false, updatable = false)
    private Users reviewer;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "purposeRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Purposes> purposes;
}