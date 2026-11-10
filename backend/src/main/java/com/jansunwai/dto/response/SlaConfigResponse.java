package com.jansunwai.dto.response;

import com.jansunwai.entity.Priority;
import com.jansunwai.entity.SlaConfiguration;

public record SlaConfigResponse(Long id, Long departmentId, String departmentName, Priority priority, int resolutionHours,
                                int warningPercent, boolean active) {
    public static SlaConfigResponse from(SlaConfiguration s) {
        return new SlaConfigResponse(s.getId(), s.getDepartment() == null ? null : s.getDepartment().getId(),
                s.getDepartment() == null ? "Default (all departments)" : s.getDepartment().getName(), s.getPriority(),
                s.getResolutionHours(), s.getWarningPercent(), s.isActive());
    }
}
