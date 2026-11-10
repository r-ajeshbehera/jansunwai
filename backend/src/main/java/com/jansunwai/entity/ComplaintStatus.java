package com.jansunwai.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * The complaint state machine. Who may trigger each move is checked in WorkflowService; WHICH moves exist is here.
 *
 *  SUBMITTED -> AI_ANALYSIS_PENDING -> UNDER_REVIEW -> APPROVED -> ASSIGNED -> IN_PROGRESS -> RESOLUTION_SUBMITTED
 *            -> RESOLVED -> CLOSED          UNDER_REVIEW -> REJECTED        RESOLUTION_SUBMITTED -> IN_PROGRESS (sent back)
 *  RESOLVED -> REOPENED -> ASSIGNED
 *
 * ESCALATED is deliberately NOT a state: escalation is a flag (escalation_level 1..2) on an open complaint, so an
 * overdue complaint keeps its real position in the workflow (e.g. IN_PROGRESS + escalated).
 */
public enum ComplaintStatus {
    SUBMITTED, AI_ANALYSIS_PENDING, UNDER_REVIEW, APPROVED, ASSIGNED, IN_PROGRESS, RESOLUTION_SUBMITTED, RESOLVED,
    CLOSED, REJECTED, REOPENED;

    public Set<ComplaintStatus> allowedNext() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(AI_ANALYSIS_PENDING);
            case AI_ANALYSIS_PENDING -> EnumSet.of(UNDER_REVIEW);
            case UNDER_REVIEW -> EnumSet.of(APPROVED, REJECTED);
            case APPROVED -> EnumSet.of(ASSIGNED);
            case ASSIGNED -> EnumSet.of(IN_PROGRESS);
            case IN_PROGRESS -> EnumSet.of(RESOLUTION_SUBMITTED);
            case RESOLUTION_SUBMITTED -> EnumSet.of(RESOLVED, IN_PROGRESS);
            case RESOLVED -> EnumSet.of(CLOSED, REOPENED);
            case REOPENED -> EnumSet.of(ASSIGNED);
            case CLOSED, REJECTED -> EnumSet.noneOf(ComplaintStatus.class);
        };
    }

    public boolean canMoveTo(ComplaintStatus next) {
        return allowedNext().contains(next);
    }

    /** Statuses where the SLA clock is running (approved and not yet resolved). */
    public boolean isSlaActive() {
        return this == APPROVED || this == ASSIGNED || this == IN_PROGRESS || this == RESOLUTION_SUBMITTED || this == REOPENED;
    }

    public boolean isFinal() {
        return this == CLOSED || this == REJECTED;
    }
}
