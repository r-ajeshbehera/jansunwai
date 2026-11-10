package com.jansunwai.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * pgvector access with plain SQL. A vector is sent as its text form "[0.1,0.2,...]" and cast with ::vector, so no
 * extra library is needed. "<=>" is pgvector's cosine-distance operator; similarity = 1 - distance.
 */
@Repository
public class EmbeddingRepository {
    private final JdbcTemplate jdbc;

    public EmbeddingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record SimilarComplaint(long complaintId, double similarity, Integer distanceMeters, String category) { }

    public void upsert(long complaintId, float[] embedding, String model) {
        jdbc.update("""
                INSERT INTO complaint_embeddings (complaint_id, embedding, model) VALUES (?, ?::vector, ?)
                ON CONFLICT (complaint_id) DO UPDATE SET embedding = EXCLUDED.embedding, model = EXCLUDED.model, created_at = now()
                """, complaintId, toLiteral(embedding), model);
    }

    public boolean exists(long complaintId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM complaint_embeddings WHERE complaint_id = ?", Integer.class, complaintId);
        return n != null && n > 0;
    }

    public List<Long> complaintsWithoutEmbedding(int limit) {
        return jdbc.queryForList("""
                SELECT c.id FROM complaints c LEFT JOIN complaint_embeddings e ON e.complaint_id = c.id
                WHERE e.complaint_id IS NULL AND c.status NOT IN ('SUBMITTED', 'AI_ANALYSIS_PENDING')
                ORDER BY c.id LIMIT ?
                """, Long.class, limit);
    }

    /**
     * Nearest older complaints by meaning (vector index), inside a time window, skipping closed/rejected ones and
     * linked children (we compare against the canonical complaint of a group). Distance comes from PostGIS.
     */
    public List<SimilarComplaint> findSimilarOlder(long complaintId, int windowDays, int limit) {
        return jdbc.query("""
                SELECT o.id, 1 - (eo.embedding <=> es.embedding) AS similarity,
                       CASE WHEN s.location IS NOT NULL AND o.location IS NOT NULL
                            THEN ST_Distance(s.location, o.location)::int END AS distance_m,
                       o.category
                FROM complaints s
                JOIN complaint_embeddings es ON es.complaint_id = s.id
                JOIN complaint_embeddings eo ON eo.complaint_id <> s.id
                JOIN complaints o ON o.id = eo.complaint_id
                LEFT JOIN duplicate_groups g ON g.id = o.duplicate_group_id
                WHERE s.id = ?
                  AND o.created_at <= s.created_at
                  AND o.created_at >= s.created_at - make_interval(days => ?)
                  AND o.status NOT IN ('REJECTED', 'CLOSED', 'SUBMITTED', 'AI_ANALYSIS_PENDING')
                  AND (o.duplicate_group_id IS NULL OR g.master_complaint_id = o.id)
                  AND (s.duplicate_group_id IS NULL OR s.duplicate_group_id IS DISTINCT FROM o.duplicate_group_id)
                ORDER BY eo.embedding <=> es.embedding
                LIMIT ?
                """, (rs, i) -> new SimilarComplaint(rs.getLong(1), rs.getDouble(2),
                        (Integer) rs.getObject(3), rs.getString(4)),
                complaintId, windowDays, limit);
    }

    static String toLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
