package com.jansunwai.dto.response;

import com.jansunwai.entity.AuditLog;

import java.time.Instant;

public record AuditResponse(Long id, Long actorId, String action, String entityType, Long entityId, String details,
                            Instant createdAt) {
    public static AuditResponse from(AuditLog a) {
        return new AuditResponse(a.getId(), a.getActorId(), a.getAction(), a.getEntityType(), a.getEntityId(), a.getDetails(),
                a.getCreatedAt());
    }
}
