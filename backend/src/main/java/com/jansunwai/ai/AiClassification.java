package com.jansunwai.ai;

import com.jansunwai.entity.Category;
import com.jansunwai.entity.Priority;

import java.math.BigDecimal;

/** A VALIDATED classification: enums are real enums, department exists, confidence is in [0, 1]. */
public record AiClassification(Category category, String departmentCode, Priority priority, String language,
                               String summaryEn, String translationEn, String suggestedActions, BigDecimal confidence) { }
