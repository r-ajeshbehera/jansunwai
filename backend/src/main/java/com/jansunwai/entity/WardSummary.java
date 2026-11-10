package com.jansunwai.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

/** Weekly ward report: SQL facts (observed) + AI narrative (interpretation), kept apart on purpose. */
@Entity
@Table(name = "ward_summaries")
public class WardSummary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ward_id", nullable = false)
    private Long wardId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "facts_json", nullable = false, columnDefinition = "jsonb")
    private String factsJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String narrative;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected WardSummary() { }

    public WardSummary(Long wardId, LocalDate weekStart, String factsJson, String narrative, String provider) {
        this.wardId = wardId;
        this.weekStart = weekStart;
        this.factsJson = factsJson;
        this.narrative = narrative;
        this.provider = provider;
    }

    public void replace(String factsJson, String narrative, String provider) {
        this.factsJson = factsJson;
        this.narrative = narrative;
        this.provider = provider;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getWardId() { return wardId; }
    public LocalDate getWeekStart() { return weekStart; }
    public String getFactsJson() { return factsJson; }
    public String getNarrative() { return narrative; }
    public String getProvider() { return provider; }
    public Instant getCreatedAt() { return createdAt; }
}
