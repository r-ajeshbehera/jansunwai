package com.jansunwai.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only timeline. changedBy NULL means the system (AI pipeline or scheduler). */
@Entity
@Table(name = "complaint_status_history")
public class ComplaintStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 25)
    private ComplaintStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 25)
    private ComplaintStatus toStatus;

    @Column(length = 500)
    private String note;

    @Column(name = "public_note", nullable = false)
    private boolean publicNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ComplaintStatusHistory() { }

    public ComplaintStatusHistory(Long complaintId, ComplaintStatus from, ComplaintStatus to, String note,
                                  boolean publicNote, User changedBy) {
        this.complaintId = complaintId;
        this.fromStatus = from;
        this.toStatus = to;
        this.note = note;
        this.publicNote = publicNote;
        this.changedBy = changedBy;
    }

    public Long getId() { return id; }
    public Long getComplaintId() { return complaintId; }
    public ComplaintStatus getFromStatus() { return fromStatus; }
    public ComplaintStatus getToStatus() { return toStatus; }
    public String getNote() { return note; }
    public boolean isPublicNote() { return publicNote; }
    public User getChangedBy() { return changedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
