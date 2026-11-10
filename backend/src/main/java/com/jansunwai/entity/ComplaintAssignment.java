package com.jansunwai.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Who works on a complaint. A partial unique index allows only ONE active row per complaint. */
@Entity
@Table(name = "complaint_assignments")
public class ComplaintAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_officer_id", nullable = false)
    private User fieldOfficer;

    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;

    @Column(length = 300)
    private String note;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    protected ComplaintAssignment() { }

    public ComplaintAssignment(Long complaintId, User fieldOfficer, Long assignedBy, String note) {
        this.complaintId = complaintId;
        this.fieldOfficer = fieldOfficer;
        this.assignedBy = assignedBy;
        this.note = note;
    }

    public void end() {
        this.active = false;
        this.endedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getComplaintId() { return complaintId; }
    public User getFieldOfficer() { return fieldOfficer; }
    public Long getAssignedBy() { return assignedBy; }
    public String getNote() { return note; }
    public boolean isActive() { return active; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getEndedAt() { return endedAt; }
}
