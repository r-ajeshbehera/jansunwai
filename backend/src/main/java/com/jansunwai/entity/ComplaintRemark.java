package com.jansunwai.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "complaint_remarks")
public class ComplaintRemark {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RemarkVisibility visibility;

    @Column(name = "info_request", nullable = false)
    private boolean infoRequest;

    @Column(nullable = false, length = 1000)
    private String text;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ComplaintRemark() { }

    public ComplaintRemark(Long complaintId, User author, RemarkVisibility visibility, boolean infoRequest, String text) {
        this.complaintId = complaintId;
        this.author = author;
        this.visibility = visibility;
        this.infoRequest = infoRequest;
        this.text = text;
    }

    public Long getId() { return id; }
    public Long getComplaintId() { return complaintId; }
    public User getAuthor() { return author; }
    public RemarkVisibility getVisibility() { return visibility; }
    public boolean isInfoRequest() { return infoRequest; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
}
