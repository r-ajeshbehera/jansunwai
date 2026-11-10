package com.jansunwai.dto.request;

import com.jansunwai.entity.Category;
import com.jansunwai.entity.Priority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Department officer's decision on an UNDER_REVIEW complaint.
 * APPROVE: category, departmentId (own department) and priority required. REROUTE: departmentId required.
 * REJECT: reason required.
 */
public record ReviewRequest(@NotNull Decision decision, Category category, Long departmentId, Priority priority,
                            @Size(max = 300) String reason, @Size(max = 500) String note) {
    public enum Decision { APPROVE, REROUTE, REJECT }
}
