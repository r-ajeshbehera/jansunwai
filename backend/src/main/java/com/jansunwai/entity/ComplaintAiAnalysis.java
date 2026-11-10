package com.jansunwai.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** One AI run for a complaint (successful or failed). Never edited: officers' decisions are stored on the complaint. */
@Entity
@Table(name = "complaint_ai_analyses")
public class ComplaintAiAnalysis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(nullable = false, length = 60)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AiStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Category category;

    @Column(name = "department_code", length = 30)
    private String departmentCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Priority priority;

    @Column(name = "detected_language", length = 5)
    private String detectedLanguage;

    @Column(name = "summary_en", length = 400)
    private String summaryEn;

    @Column(name = "translation_en", columnDefinition = "TEXT")
    private String translationEn;

    @Column(name = "suggested_actions", length = 600)
    private String suggestedActions;

    @Column(precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview = true;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "cost_usd", nullable = false, precision = 10, scale = 6)
    private BigDecimal costUsd = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ComplaintAiAnalysis() { }

    public static ComplaintAiAnalysis succeeded(Long complaintId, String provider, String model, Category category,
                                                String departmentCode, Priority priority, String language, String summaryEn,
                                                String translationEn, String suggestedActions, BigDecimal confidence,
                                                boolean needsReview, int latencyMs, Integer inTokens, Integer outTokens,
                                                BigDecimal costUsd) {
        ComplaintAiAnalysis a = new ComplaintAiAnalysis();
        a.complaintId = complaintId;
        a.provider = provider;
        a.model = model;
        a.status = AiStatus.SUCCEEDED;
        a.category = category;
        a.departmentCode = departmentCode;
        a.priority = priority;
        a.detectedLanguage = language;
        a.summaryEn = summaryEn;
        a.translationEn = translationEn;
        a.suggestedActions = suggestedActions;
        a.confidence = confidence;
        a.needsReview = needsReview;
        a.latencyMs = latencyMs;
        a.inputTokens = inTokens;
        a.outputTokens = outTokens;
        a.costUsd = costUsd;
        return a;
    }

    public static ComplaintAiAnalysis failed(Long complaintId, String provider, String model, String errorCode, int latencyMs) {
        ComplaintAiAnalysis a = new ComplaintAiAnalysis();
        a.complaintId = complaintId;
        a.provider = provider;
        a.model = model;
        a.status = AiStatus.FAILED;
        a.errorCode = errorCode;
        a.latencyMs = latencyMs;
        return a;
    }

    public Long getId() { return id; }
    public Long getComplaintId() { return complaintId; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public AiStatus getStatus() { return status; }
    public Category getCategory() { return category; }
    public String getDepartmentCode() { return departmentCode; }
    public Priority getPriority() { return priority; }
    public String getDetectedLanguage() { return detectedLanguage; }
    public String getSummaryEn() { return summaryEn; }
    public String getTranslationEn() { return translationEn; }
    public String getSuggestedActions() { return suggestedActions; }
    public BigDecimal getConfidence() { return confidence; }
    public boolean isNeedsReview() { return needsReview; }
    public String getErrorCode() { return errorCode; }
    public Integer getLatencyMs() { return latencyMs; }
    public Integer getInputTokens() { return inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public BigDecimal getCostUsd() { return costUsd; }
    public Instant getCreatedAt() { return createdAt; }
}
