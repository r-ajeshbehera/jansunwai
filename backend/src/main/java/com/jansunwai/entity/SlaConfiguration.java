package com.jansunwai.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** department NULL = the default policy used when a department has no specific rule. */
@Entity
@Table(name = "sla_configurations")
public class SlaConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Column(name = "resolution_hours", nullable = false)
    private int resolutionHours;

    @Column(name = "warning_percent", nullable = false)
    private int warningPercent;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SlaConfiguration() { }

    public SlaConfiguration(Department department, Priority priority, int resolutionHours, int warningPercent) {
        this.department = department;
        this.priority = priority;
        this.resolutionHours = resolutionHours;
        this.warningPercent = warningPercent;
    }

    public void update(int resolutionHours, int warningPercent, boolean active) {
        this.resolutionHours = resolutionHours;
        this.warningPercent = warningPercent;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Department getDepartment() { return department; }
    public Priority getPriority() { return priority; }
    public int getResolutionHours() { return resolutionHours; }
    public int getWarningPercent() { return warningPercent; }
    public boolean isActive() { return active; }
    public Instant getUpdatedAt() { return updatedAt; }
}
