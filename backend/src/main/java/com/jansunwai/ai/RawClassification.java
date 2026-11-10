package com.jansunwai.ai;

/** The JSON shape we ask the model for. Every field is a plain String/Double because the model may return anything. */
public record RawClassification(String category, String department, String priority, String language, String summary,
                                String translation, String suggestedActions, Double confidence) { }
