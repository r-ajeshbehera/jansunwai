package com.jansunwai.dto.response;

import com.jansunwai.entity.*;

import java.time.Instant;

/** One row in a complaint list. No personal data. */
public record ComplaintSummary(Long id, String trackingId, String title, ComplaintStatus status, Category category,
                               Priority priority, ClassificationSource classificationSource, String departmentName,
                               String wardName, Instant createdAt, Instant slaDueAt, boolean overdue, int escalationLevel,
                               boolean linkedDuplicate) {
    public static ComplaintSummary from(Complaint c) {
        return new ComplaintSummary(c.getId(), c.getTrackingId(), c.getTitle(), c.getStatus(), c.getCategory(),
                c.getPriority(), c.getClassificationSource(),
                c.getDepartment() == null ? null : c.getDepartment().getName(),
                c.getWard() == null ? null : c.getWard().getName(), c.getCreatedAt(), c.getSlaDueAt(), isOverdue(c),
                c.getEscalationLevel(), c.getDuplicateGroup() != null && !c.getDuplicateGroup().getMasterComplaintId().equals(c.getId()));
    }

    public static boolean isOverdue(Complaint c) {
        return c.getStatus().isSlaActive() && c.getSlaDueAt() != null && c.getSlaDueAt().isBefore(Instant.now());
    }
}
