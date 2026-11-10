package com.jansunwai.dto.response;

import java.time.Instant;

public record AssignmentResponse(Long fieldOfficerId, String fieldOfficerName, String note, boolean active,
                                 Instant assignedAt, Instant endedAt) { }
