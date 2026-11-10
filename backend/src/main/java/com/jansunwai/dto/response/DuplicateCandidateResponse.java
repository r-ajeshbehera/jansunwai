package com.jansunwai.dto.response;

import com.jansunwai.entity.CandidateStatus;
import com.jansunwai.entity.DuplicateCandidate;

import java.math.BigDecimal;
import java.time.Instant;

public record DuplicateCandidateResponse(Long id, ComplaintSummary complaint, ComplaintSummary candidate,
                                         BigDecimal semanticScore, Integer distanceMeters, BigDecimal totalScore,
                                         CandidateStatus status, Instant createdAt) {
    public static DuplicateCandidateResponse from(DuplicateCandidate dc) {
        return new DuplicateCandidateResponse(dc.getId(), ComplaintSummary.from(dc.getComplaint()),
                ComplaintSummary.from(dc.getCandidate()), dc.getSemanticScore(), dc.getDistanceM(), dc.getTotalScore(),
                dc.getStatus(), dc.getCreatedAt());
    }
}
