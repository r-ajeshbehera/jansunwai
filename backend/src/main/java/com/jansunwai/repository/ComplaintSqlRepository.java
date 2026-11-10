package com.jansunwai.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Conditional single-statement updates used by schedulers. Each one is idempotent ("... WHERE sla_warned_at IS NULL")
 * and bumps "version", so an officer editing the same complaint at that moment gets a clean optimistic-lock 409
 * instead of silently overwriting the scheduler's change with stale data.
 */
@Repository
public class ComplaintSqlRepository {
    private final JdbcTemplate jdbc;

    public ComplaintSqlRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true only for the one caller that actually set the flag. */
    public boolean markSlaWarned(long complaintId) {
        return jdbc.update("""
                UPDATE complaints SET sla_warned_at = now(), version = version + 1, updated_at = now()
                WHERE id = ? AND sla_warned_at IS NULL
                """, complaintId) == 1;
    }

    /** Raise the escalation level, never lower it. */
    public boolean raiseEscalationLevel(long complaintId, int level) {
        return jdbc.update("""
                UPDATE complaints SET escalation_level = ?, version = version + 1, updated_at = now()
                WHERE id = ? AND escalation_level < ?
                """, level, complaintId, level) == 1;
    }

    public long nextTrackingNumber() {
        Long n = jdbc.queryForObject("SELECT nextval('complaint_tracking_seq')", Long.class);
        return n == null ? 0 : n;
    }
}
