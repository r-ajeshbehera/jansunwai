package com.jansunwai.entity;

import com.jansunwai.exception.InvalidStateException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A citizen's complaint. The original title/description are never changed after submission; AI results live in
 * complaint_ai_analyses. The PostGIS "location" column is GENERATED from latitude/longitude by the database.
 * @Version prevents two officers from changing the same complaint at the same time (lost update).
 */
@Entity
@Table(name = "complaints")
public class Complaint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_id", nullable = false, unique = true, updatable = false, length = 20)
    private String trackingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citizen_id", nullable = false, updatable = false)
    private User citizen;

    @Column(nullable = false, updatable = false, length = 150)
    private String title;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 5)
    private String language;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_id")
    private Ward ward;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(length = 200)
    private String landmark;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification_source", nullable = false, length = 20)
    private ClassificationSource classificationSource = ClassificationSource.NONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classified_by")
    private User classifiedBy;

    @Column(name = "classified_at")
    private Instant classifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private ComplaintStatus status = ComplaintStatus.SUBMITTED;

    @Column(name = "ai_attempts", nullable = false)
    private int aiAttempts;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_group_id")
    private DuplicateGroup duplicateGroup;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "sla_due_at")
    private Instant slaDueAt;

    @Column(name = "sla_warned_at")
    private Instant slaWarnedAt;

    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "rejection_reason", length = 300)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private Long version;

    protected Complaint() { }

    public Complaint(String trackingId, User citizen, String title, String description, String language, Ward ward,
                     BigDecimal latitude, BigDecimal longitude, String landmark) {
        this.trackingId = trackingId;
        this.citizen = citizen;
        this.title = title;
        this.description = description;
        this.language = language;
        this.ward = ward;
        this.latitude = latitude;
        this.longitude = longitude;
        this.landmark = landmark;
    }

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    // ---- state machine -----------------------------------------------------------------

    /** The only generic way to change status. Returns the previous status for the history row. */
    public ComplaintStatus moveTo(ComplaintStatus next) {
        if (!status.canMoveTo(next)) {
            throw new InvalidStateException("Complaint " + trackingId + " cannot move from " + status + " to " + next);
        }
        ComplaintStatus from = status;
        status = next;
        Instant now = Instant.now();
        if (next == ComplaintStatus.RESOLVED) resolvedAt = now;
        if (next == ComplaintStatus.CLOSED) closedAt = now;
        if (next == ComplaintStatus.REOPENED) resolvedAt = null;
        return from;
    }

    /** Linked duplicates follow their master; this is the only path to RESOLVED that skips the normal sequence. */
    public ComplaintStatus resolveWithMaster() {
        if (!status.isSlaActive() && status != ComplaintStatus.UNDER_REVIEW) {
            throw new InvalidStateException("Complaint " + trackingId + " cannot be resolved from " + status);
        }
        ComplaintStatus from = status;
        status = ComplaintStatus.RESOLVED;
        resolvedAt = Instant.now();
        return from;
    }

    public void suggestClassification(Category category, Department department, Priority priority) {
        this.category = category;
        this.department = department;
        this.priority = priority;
        this.classificationSource = ClassificationSource.AI_SUGGESTED;
    }

    /** Human decision. "corrected" = the officer changed what the AI suggested. */
    public void approveClassification(Category category, Department department, Priority priority, User officer,
                                      boolean corrected, Instant slaDueAt) {
        this.category = category;
        this.department = department;
        this.priority = priority;
        this.classificationSource = corrected ? ClassificationSource.OFFICER_CORRECTED : ClassificationSource.OFFICER_CONFIRMED;
        this.classifiedBy = officer;
        this.classifiedAt = Instant.now();
        this.acceptedAt = Instant.now();
        this.slaDueAt = slaDueAt;
    }

    public void reroute(Department department) {
        this.department = department;
    }

    public void reject(String reason) {
        this.rejectionReason = reason;
    }

    public void linkTo(DuplicateGroup group) {
        this.duplicateGroup = group;
    }

    public boolean isLinkedDuplicate() {
        return duplicateGroup != null && !duplicateGroup.getMasterComplaintId().equals(id);
    }

    public void incrementAiAttempts() { aiAttempts++; }

    // ---- getters ---------------------------------------------------------------------
    public Long getId() { return id; }
    public String getTrackingId() { return trackingId; }
    public User getCitizen() { return citizen; }
    public Long getCitizenId() { return citizen.getId(); }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getLanguage() { return language; }
    public Ward getWard() { return ward; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public boolean hasLocation() { return latitude != null && longitude != null; }
    public String getLandmark() { return landmark; }
    public Category getCategory() { return category; }
    public Department getDepartment() { return department; }
    public Long getDepartmentId() { return department == null ? null : department.getId(); }
    public Priority getPriority() { return priority; }
    public ClassificationSource getClassificationSource() { return classificationSource; }
    public User getClassifiedBy() { return classifiedBy; }
    public Instant getClassifiedAt() { return classifiedAt; }
    public ComplaintStatus getStatus() { return status; }
    public int getAiAttempts() { return aiAttempts; }
    public DuplicateGroup getDuplicateGroup() { return duplicateGroup; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getSlaDueAt() { return slaDueAt; }
    public Instant getSlaWarnedAt() { return slaWarnedAt; }
    public int getEscalationLevel() { return escalationLevel; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getClosedAt() { return closedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    public void setWard(Ward ward) { this.ward = ward; }
    public void setIdForTest(Long id) { this.id = id; }
    public void setStatusForTest(ComplaintStatus status) { this.status = status; }
    public void setSlaForTest(Instant acceptedAt, Instant dueAt) { this.acceptedAt = acceptedAt; this.slaDueAt = dueAt; }
}
