package com.jansunwai.dto.response;

import com.jansunwai.entity.*;

import java.math.BigDecimal;
import java.time.Instant;

public record AiAnalysisResponse(Long id, String provider, String model, AiStatus status, Category category,
                                 String departmentCode, Priority priority, String detectedLanguage, String summaryEn,
                                 String translationEn, String suggestedActions, BigDecimal confidence, boolean needsReview,
                                 String errorCode, Integer latencyMs, Instant createdAt, boolean mock) {
    public static AiAnalysisResponse from(ComplaintAiAnalysis a) {
        return new AiAnalysisResponse(a.getId(), a.getProvider(), a.getModel(), a.getStatus(), a.getCategory(),
                a.getDepartmentCode(), a.getPriority(), a.getDetectedLanguage(), a.getSummaryEn(), a.getTranslationEn(),
                a.getSuggestedActions(), a.getConfidence(), a.isNeedsReview(), a.getErrorCode(), a.getLatencyMs(),
                a.getCreatedAt(), "mock".equals(a.getProvider()));
    }
}
