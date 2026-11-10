package com.jansunwai.dto.request;

import com.jansunwai.entity.Priority;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** departmentId null = default policy. */
public record SlaConfigRequest(Long departmentId, @NotNull Priority priority,
                               @NotNull @Min(1) @Max(2160) Integer resolutionHours,
                               @NotNull @Min(10) @Max(95) Integer warningPercent, Boolean active) { }
