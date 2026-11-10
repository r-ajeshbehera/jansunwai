package com.jansunwai.service;

import com.jansunwai.dto.response.AiUsageResponse;
import com.jansunwai.repository.ComplaintAiAnalysisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** API usage and cost monitoring per provider/model (from complaint_ai_analyses). */
@Service
public class AiUsageService {
    private final ComplaintAiAnalysisRepository analyses;

    public AiUsageService(ComplaintAiAnalysisRepository analyses) {
        this.analyses = analyses;
    }

    @Transactional(readOnly = true)
    public List<AiUsageResponse> usage() {
        return analyses.usageByProvider().stream().map(r -> new AiUsageResponse((String) r[0], (String) r[1],
                ((Number) r[2]).longValue(), ((Number) r[3]).longValue(), ((Number) r[4]).longValue(),
                ((Number) r[5]).longValue(), r[6] instanceof BigDecimal b ? b : new BigDecimal(r[6].toString()),
                Math.round(((Number) r[7]).doubleValue() * 10) / 10.0)).toList();
    }
}
