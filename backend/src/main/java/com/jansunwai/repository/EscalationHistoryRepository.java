package com.jansunwai.repository;

import com.jansunwai.entity.EscalationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface EscalationHistoryRepository extends JpaRepository<EscalationHistory, Long> {

    /**
     * Idempotent insert: ON CONFLICT DO NOTHING on UNIQUE(complaint_id, level).
     * Returns 1 if this call created the escalation, 0 if it already existed (e.g. the job ran twice).
     */
    @Modifying
    @Query(value = """
           INSERT INTO escalation_history (complaint_id, level, reason, due_at)
           VALUES (:complaintId, :level, :reason, :dueAt)
           ON CONFLICT (complaint_id, level) DO NOTHING
           """, nativeQuery = true)
    int insertIfAbsent(@Param("complaintId") Long complaintId, @Param("level") int level, @Param("reason") String reason,
                       @Param("dueAt") Instant dueAt);

    List<EscalationHistory> findByComplaintIdOrderByLevel(Long complaintId);

    Page<EscalationHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select e from EscalationHistory e where e.complaintId in " +
           "(select c.id from Complaint c where c.department.id = :departmentId) order by e.createdAt desc")
    Page<EscalationHistory> findForDepartment(@Param("departmentId") Long departmentId, Pageable pageable);
}
