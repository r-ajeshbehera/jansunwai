package com.jansunwai.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "complaint_id")
    private Long complaintId;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Notification() { }

    public Notification(Long userId, Long complaintId, String type, String message) {
        this.userId = userId;
        this.complaintId = complaintId;
        this.type = type;
        this.message = message.length() > 500 ? message.substring(0, 500) : message;
    }

    public void markRead() { if (readAt == null) readAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getComplaintId() { return complaintId; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
