package com.jansunwai.dto.response;

import com.jansunwai.ai.WardFacts;

import java.time.Instant;

/** facts = observed numbers from SQL. narrative = AI interpretation (label it as such in the UI). */
public record WardSummaryResponse(Long wardId, String weekStart, WardFacts facts, String narrative, String provider,
                                  boolean aiGenerated, Instant generatedAt) { }
