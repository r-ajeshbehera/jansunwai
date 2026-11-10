package com.jansunwai.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "escalation_history")
public class EscalationHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected EscalationHistory() { }

    public Long getId() { return id; }
    public Long getComplaintId() { return complaintId; }
    public int getLevel() { return level; }
    public String getReason() { return reason; }
    public Instant getDueAt() { return dueAt; }
    public Instant getCreatedAt() { return createdAt; }
}
