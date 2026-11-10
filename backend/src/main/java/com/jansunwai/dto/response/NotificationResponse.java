package com.jansunwai.dto.response;

import com.jansunwai.entity.Notification;

import java.time.Instant;

public record NotificationResponse(Long id, Long complaintId, String type, String message, boolean read, Instant createdAt) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getComplaintId(), n.getType(), n.getMessage(), n.getReadAt() != null,
                n.getCreatedAt());
    }
}
