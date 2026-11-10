package com.jansunwai.unit;

import com.jansunwai.entity.ComplaintStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static com.jansunwai.entity.ComplaintStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class ComplaintStatusTest {

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({"SUBMITTED,AI_ANALYSIS_PENDING", "AI_ANALYSIS_PENDING,UNDER_REVIEW", "UNDER_REVIEW,APPROVED",
            "UNDER_REVIEW,REJECTED", "APPROVED,ASSIGNED", "ASSIGNED,IN_PROGRESS", "IN_PROGRESS,RESOLUTION_SUBMITTED",
            "RESOLUTION_SUBMITTED,RESOLVED", "RESOLUTION_SUBMITTED,IN_PROGRESS", "RESOLVED,CLOSED", "RESOLVED,REOPENED",
            "REOPENED,ASSIGNED"})
    void allowedTransitions(ComplaintStatus from, ComplaintStatus to) {
        assertThat(from.canMoveTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({"SUBMITTED,RESOLVED", "UNDER_REVIEW,ASSIGNED", "APPROVED,RESOLVED", "ASSIGNED,RESOLVED",
            "IN_PROGRESS,RESOLVED", "IN_PROGRESS,CLOSED", "REJECTED,APPROVED", "CLOSED,REOPENED", "RESOLVED,IN_PROGRESS"})
    void invalidTransitions(ComplaintStatus from, ComplaintStatus to) {
        assertThat(from.canMoveTo(to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ComplaintStatus.class, names = {"CLOSED", "REJECTED"})
    void finalStatesHaveNoExit(ComplaintStatus s) {
        assertThat(s.allowedNext()).isEmpty();
        assertThat(s.isFinal()).isTrue();
    }

    @Test
    void slaClockRunsOnlyBetweenApprovalAndResolution() {
        assertThat(APPROVED.isSlaActive()).isTrue();
        assertThat(IN_PROGRESS.isSlaActive()).isTrue();
        assertThat(REOPENED.isSlaActive()).isTrue();
        assertThat(UNDER_REVIEW.isSlaActive()).isFalse();
        assertThat(RESOLVED.isSlaActive()).isFalse();
        assertThat(CLOSED.isSlaActive()).isFalse();
    }

    @Test
    void noStatusCanJumpBackToSubmitted() {
        for (ComplaintStatus s : values()) assertThat(s.canMoveTo(SUBMITTED)).isFalse();
    }
}
