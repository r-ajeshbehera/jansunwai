package com.jansunwai.entity;

/** Where the current classification came from. Only OFFICER_* values allow a complaint to be APPROVED. */
public enum ClassificationSource { NONE, AI_SUGGESTED, OFFICER_CONFIRMED, OFFICER_CORRECTED }
