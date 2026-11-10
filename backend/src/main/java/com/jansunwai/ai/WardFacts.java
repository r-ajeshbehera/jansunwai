package com.jansunwai.ai;

import java.util.Map;

/** Observed numbers for one ward and week, computed by SQL. The AI may only interpret these, never add numbers. */
public record WardFacts(String wardName, String weekStart, long received, long resolved, long open, long overdue,
                        Map<String, Long> byCategory, String topCategory, long hotspotCount) { }
