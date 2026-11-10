package com.jansunwai.dto.request;

import com.jansunwai.entity.ComplaintStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusChangeRequest(@NotNull ComplaintStatus status, @Size(max = 500) String note) { }
