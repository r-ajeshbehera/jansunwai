package com.jansunwai.dto.response;

import com.jansunwai.entity.RemarkVisibility;

import java.time.Instant;

public record RemarkResponse(Long id, String author, RemarkVisibility visibility, boolean infoRequest, String text,
                             Instant createdAt) { }
