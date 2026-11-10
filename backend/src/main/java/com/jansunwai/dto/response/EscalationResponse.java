package com.jansunwai.dto.response;

import java.time.Instant;

public record EscalationResponse(Long id, Long complaintId, String trackingId, int level, String reason, Instant dueAt,
                                 Instant createdAt) { }
