package com.jansunwai.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** A photo stored on disk under a random name; only metadata lives in the database. */
@Entity
@Table(name = "complaint_attachments")
public class ComplaintAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AttachmentKind kind;

    @Column(name = "stored_name", nullable = false, unique = true, length = 80)
    private String storedName;

    @Column(name = "original_name", nullable = false, length = 150)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ComplaintAttachment() { }

    public ComplaintAttachment(Long complaintId, AttachmentKind kind, String storedName, String originalName,
                               String contentType, long sizeBytes, Long uploadedBy) {
        this.complaintId = complaintId;
        this.kind = kind;
        this.storedName = storedName;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
    }

    public Long getId() { return id; }
    public Long getComplaintId() { return complaintId; }
    public AttachmentKind getKind() { return kind; }
    public String getStoredName() { return storedName; }
    public String getOriginalName() { return originalName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public Long getUploadedBy() { return uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
