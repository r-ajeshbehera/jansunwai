package com.jansunwai.dto.response;

import com.jansunwai.entity.AttachmentKind;
import com.jansunwai.entity.ComplaintAttachment;

import java.time.Instant;

public record AttachmentResponse(Long id, AttachmentKind kind, String originalName, String contentType, long sizeBytes,
                                 Instant createdAt) {
    public static AttachmentResponse from(ComplaintAttachment a) {
        return new AttachmentResponse(a.getId(), a.getKind(), a.getOriginalName(), a.getContentType(), a.getSizeBytes(),
                a.getCreatedAt());
    }
}
