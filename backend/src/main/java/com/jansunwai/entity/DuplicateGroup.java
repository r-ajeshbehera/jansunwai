package com.jansunwai.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** A set of complaints about the same real-world issue. Every member keeps its own tracking ID and history. */
@Entity
@Table(name = "duplicate_groups")
public class DuplicateGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "master_complaint_id", nullable = false, unique = true)
    private Long masterComplaintId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected DuplicateGroup() { }

    public DuplicateGroup(Long masterComplaintId, Long createdBy) {
        this.masterComplaintId = masterComplaintId;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public Long getMasterComplaintId() { return masterComplaintId; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setIdForTest(Long id) { this.id = id; }
}
