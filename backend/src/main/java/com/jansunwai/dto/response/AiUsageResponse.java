package com.jansunwai.dto.response;

import java.math.BigDecimal;

public record AiUsageResponse(String provider, String model, long calls, long failures, long inputTokens, long outputTokens,
                              BigDecimal costUsd, double avgLatencyMs) { }
