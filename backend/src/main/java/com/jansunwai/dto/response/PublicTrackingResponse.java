package com.jansunwai.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * What ANYONE with a tracking ID may see. No title/description (they may contain personal details), no location,
 * no names, no phone numbers, no internal notes, no attachments.
 */
public record PublicTrackingResponse(String trackingId, String category, String status, String wardName,
                                     String departmentName, Instant submittedAt, Instant expectedBy, boolean overdue,
                                     String linkedToTrackingId, List<PublicEvent> history) {
    public record PublicEvent(String status, String note, Instant at) { }
}
