package com.jansunwai.entity;

import com.jansunwai.exception.InvalidStateException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** "Complaint X might be the same issue as older complaint Y" — a suggestion waiting for a human decision. */
@Entity
@Table(name = "duplicate_candidates")
public class DuplicateCandidate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Complaint candidate;

    @Column(name = "semantic_score", nullable = false, precision = 4, scale = 3)
    private BigDecimal semanticScore;

    @Column(name = "distance_m")
    private Integer distanceM;

    @Column(name = "total_score", nullable = false, precision = 4, scale = 3)
    private BigDecimal totalScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CandidateStatus status = CandidateStatus.PENDING;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected DuplicateCandidate() { }

    public DuplicateCandidate(Complaint complaint, Complaint candidate, BigDecimal semanticScore, Integer distanceM,
                              BigDecimal totalScore) {
        this.complaint = complaint;
        this.candidate = candidate;
        this.semanticScore = semanticScore;
        this.distanceM = distanceM;
        this.totalScore = totalScore;
    }

    public void decide(CandidateStatus decision, Long officerId) {
        if (status != CandidateStatus.PENDING) throw new InvalidStateException("This duplicate suggestion was already decided");
        if (decision == CandidateStatus.PENDING) throw new IllegalArgumentException("Decision must be APPROVED or REJECTED");
        this.status = decision;
        this.decidedBy = officerId;
        this.decidedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Complaint getComplaint() { return complaint; }
    public Complaint getCandidate() { return candidate; }
    public BigDecimal getSemanticScore() { return semanticScore; }
    public Integer getDistanceM() { return distanceM; }
    public BigDecimal getTotalScore() { return totalScore; }
    public CandidateStatus getStatus() { return status; }
    public Long getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
