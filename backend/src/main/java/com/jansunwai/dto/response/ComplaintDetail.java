package com.jansunwai.dto.response;

import com.jansunwai.entity.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Full view of one complaint, already filtered for the viewer by ComplaintViewMapper:
 * citizens never see internal notes, officer names, AI internals or other citizens' data.
 */
public record ComplaintDetail(Long id, String trackingId, String title, String description, String language,
                              ComplaintStatus status, Category category, Priority priority,
                              ClassificationSource classificationSource, Long departmentId, String departmentName,
                              Long wardId, String wardName, BigDecimal latitude, BigDecimal longitude, String landmark,
                              String citizenName, Instant createdAt, Instant acceptedAt, Instant slaDueAt, boolean overdue,
                              int escalationLevel, Instant resolvedAt, Instant closedAt, String rejectionReason,
                              AssignmentResponse assignment, AiAnalysisResponse aiAnalysis, DuplicateInfo duplicate,
                              List<AttachmentResponse> attachments, List<TimelineEntry> timeline,
                              List<RemarkResponse> remarks, List<String> allowedActions) {

    public record DuplicateInfo(String masterTrackingId, boolean isMaster, List<String> linkedTrackingIds) { }
}
