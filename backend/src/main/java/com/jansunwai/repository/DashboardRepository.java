package com.jansunwai.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregate SQL for dashboards. Every number shown on a dashboard (and every number given to the AI for ward
 * summaries) comes from these queries. Filters: departmentId / wardId may be null (= all).
 */
@Repository
public class DashboardRepository {
    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Totals(long total, long pending, long resolved, long overdue, long underReview, long rejected,
                         Double avgResolutionHours) { }
    public record Count(String key, String label, long count) { }
    public record DepartmentPerformance(long departmentId, String department, long total, long open, long resolved,
                                        long overdue, Double avgResolutionHours, Double onTimePercent) { }

    private static final String FILTER = " AND (?::bigint IS NULL OR c.department_id = ?) AND (?::bigint IS NULL OR c.ward_id = ?)"
            + " AND c.created_at >= ? AND c.created_at < ? ";

    private Object[] args(Long dept, Long ward, Instant from, Instant to) {
        return new Object[]{dept, dept, ward, ward, Timestamp.from(from), Timestamp.from(to)};
    }

    public Totals totals(Long dept, Long ward, Instant from, Instant to) {
        return jdbc.queryForObject("""
                SELECT count(*),
                       count(*) FILTER (WHERE c.status IN ('APPROVED','ASSIGNED','IN_PROGRESS','RESOLUTION_SUBMITTED','REOPENED')),
                       count(*) FILTER (WHERE c.status IN ('RESOLVED','CLOSED')),
                       count(*) FILTER (WHERE c.status IN ('APPROVED','ASSIGNED','IN_PROGRESS','RESOLUTION_SUBMITTED','REOPENED')
                                        AND c.sla_due_at < now()),
                       count(*) FILTER (WHERE c.status IN ('SUBMITTED','AI_ANALYSIS_PENDING','UNDER_REVIEW')),
                       count(*) FILTER (WHERE c.status = 'REJECTED'),
                       avg(EXTRACT(EPOCH FROM (c.resolved_at - c.accepted_at)) / 3600)
                         FILTER (WHERE c.resolved_at IS NOT NULL AND c.accepted_at IS NOT NULL)
                FROM complaints c WHERE 1 = 1
                """ + FILTER,
                (rs, i) -> new Totals(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5),
                        rs.getLong(6), rs.getObject(7) == null ? null : round1(rs.getDouble(7))),
                args(dept, ward, from, to));
    }

    public List<Count> byCategory(Long dept, Long ward, Instant from, Instant to) {
        return jdbc.query("SELECT COALESCE(c.category, 'UNCLASSIFIED'), count(*) FROM complaints c WHERE 1 = 1 " + FILTER
                        + " GROUP BY 1 ORDER BY 2 DESC",
                (rs, i) -> new Count(rs.getString(1), rs.getString(1), rs.getLong(2)), args(dept, ward, from, to));
    }

    public List<Count> byStatus(Long dept, Long ward, Instant from, Instant to) {
        return jdbc.query("SELECT c.status, count(*) FROM complaints c WHERE 1 = 1 " + FILTER + " GROUP BY 1 ORDER BY 2 DESC",
                (rs, i) -> new Count(rs.getString(1), rs.getString(1), rs.getLong(2)), args(dept, ward, from, to));
    }

    public List<Count> byWard(Long dept, Instant from, Instant to) {
        return jdbc.query("""
                SELECT COALESCE(w.code, '-'), COALESCE(w.name, 'No ward'), count(*) FROM complaints c
                LEFT JOIN wards w ON w.id = c.ward_id
                WHERE (?::bigint IS NULL OR c.department_id = ?) AND c.created_at >= ? AND c.created_at < ?
                GROUP BY 1, 2 ORDER BY 3 DESC
                """, (rs, i) -> new Count(rs.getString(1), rs.getString(2), rs.getLong(3)),
                dept, dept, Timestamp.from(from), Timestamp.from(to));
    }

    public List<DepartmentPerformance> departmentPerformance(Instant from, Instant to) {
        return jdbc.query("""
                SELECT d.id, d.name, count(c.id),
                       count(c.id) FILTER (WHERE c.status IN ('APPROVED','ASSIGNED','IN_PROGRESS','RESOLUTION_SUBMITTED','REOPENED')),
                       count(c.id) FILTER (WHERE c.status IN ('RESOLVED','CLOSED')),
                       count(c.id) FILTER (WHERE c.status IN ('APPROVED','ASSIGNED','IN_PROGRESS','RESOLUTION_SUBMITTED','REOPENED')
                                           AND c.sla_due_at < now()),
                       avg(EXTRACT(EPOCH FROM (c.resolved_at - c.accepted_at)) / 3600) FILTER (WHERE c.resolved_at IS NOT NULL),
                       100.0 * count(c.id) FILTER (WHERE c.resolved_at IS NOT NULL AND c.resolved_at <= c.sla_due_at)
                             / NULLIF(count(c.id) FILTER (WHERE c.resolved_at IS NOT NULL), 0)
                FROM departments d
                LEFT JOIN complaints c ON c.department_id = d.id AND c.created_at >= ? AND c.created_at < ?
                GROUP BY d.id, d.name ORDER BY d.name
                """, (rs, i) -> new DepartmentPerformance(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getLong(4),
                        rs.getLong(5), rs.getLong(6), rs.getObject(7) == null ? null : round1(rs.getDouble(7)),
                        rs.getObject(8) == null ? null : round1(rs.getDouble(8))),
                Timestamp.from(from), Timestamp.from(to));
    }

    /** Opened vs resolved per day, for the trend chart. */
    public List<Map<String, Object>> dailyTrend(Long dept, Long ward, Instant from, Instant to) {
        return jdbc.query("""
                SELECT d::date AS day,
                       (SELECT count(*) FROM complaints c WHERE c.created_at::date = d::date
                          AND (?::bigint IS NULL OR c.department_id = ?) AND (?::bigint IS NULL OR c.ward_id = ?)) AS opened,
                       (SELECT count(*) FROM complaints c WHERE c.resolved_at::date = d::date
                          AND (?::bigint IS NULL OR c.department_id = ?) AND (?::bigint IS NULL OR c.ward_id = ?)) AS resolved
                FROM generate_series(?::timestamptz, ?::timestamptz - interval '1 day', interval '1 day') d
                ORDER BY 1
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", rs.getDate(1).toLocalDate().toString());
            m.put("opened", rs.getLong(2));
            m.put("resolved", rs.getLong(3));
            return m;
        }, dept, dept, ward, ward, dept, dept, ward, ward, Timestamp.from(from), Timestamp.from(to));
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
