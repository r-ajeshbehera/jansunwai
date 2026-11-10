package com.jansunwai.dto.response;

import com.jansunwai.entity.ComplaintStatus;

import java.time.Instant;

public record TimelineEntry(ComplaintStatus fromStatus, ComplaintStatus toStatus, String note, String actor,
                            boolean publicNote, Instant at) { }
