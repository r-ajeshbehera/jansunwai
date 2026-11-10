package com.jansunwai.repository;

import com.jansunwai.entity.Complaint;
import com.jansunwai.entity.ComplaintStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    /** join fetch = one query instead of 1 + N lazy loads (N+1 problem). */
    @Query("""
           select c from Complaint c join fetch c.citizen left join fetch c.ward left join fetch c.department
           left join fetch c.duplicateGroup where c.id = :id
           """)
    Optional<Complaint> findWithDetails(@Param("id") Long id);

    @Query("""
           select c from Complaint c left join fetch c.ward left join fetch c.department left join fetch c.duplicateGroup
           where c.trackingId = :trackingId
           """)
    Optional<Complaint> findByTrackingId(@Param("trackingId") String trackingId);

    @Query(value = """
           select c from Complaint c left join fetch c.ward left join fetch c.department
           where c.citizen.id = :citizenId and (:status is null or c.status = :status)
           """,
           countQuery = "select count(c) from Complaint c where c.citizen.id = :citizenId and (:status is null or c.status = :status)")
    Page<Complaint> findForCitizen(@Param("citizenId") Long citizenId, @Param("status") ComplaintStatus status, Pageable pageable);

    /**
     * Staff list. departmentId null = all departments (admin). includeUnrouted = also complaints that AI could not route
     * (department still null), so any department officer can pick them up.
     */
    @Query(value = """
           select c from Complaint c left join fetch c.ward w left join fetch c.department d
           where ((:departmentId is null) or d.id = :departmentId or (:includeUnrouted = true and d.id is null))
             and (:status is null or c.status = :status)
             and (:wardId is null or w.id = :wardId)
             and (:overdueOnly = false or (c.slaDueAt < :now and c.status in :slaStatuses))
             and (:q is null or lower(c.trackingId) like concat('%', :q, '%') or lower(c.title) like concat('%', :q, '%'))
           """,
           countQuery = """
           select count(c) from Complaint c left join c.ward w left join c.department d
           where ((:departmentId is null) or d.id = :departmentId or (:includeUnrouted = true and d.id is null))
             and (:status is null or c.status = :status)
             and (:wardId is null or w.id = :wardId)
             and (:overdueOnly = false or (c.slaDueAt < :now and c.status in :slaStatuses))
             and (:q is null or lower(c.trackingId) like concat('%', :q, '%') or lower(c.title) like concat('%', :q, '%'))
           """)
    Page<Complaint> searchForStaff(@Param("departmentId") Long departmentId, @Param("includeUnrouted") boolean includeUnrouted,
                                   @Param("status") ComplaintStatus status, @Param("wardId") Long wardId,
                                   @Param("overdueOnly") boolean overdueOnly, @Param("now") Instant now,
                                   @Param("slaStatuses") Collection<ComplaintStatus> slaStatuses, @Param("q") String q,
                                   Pageable pageable);

    @Query(value = """
           select c from Complaint c left join fetch c.ward left join fetch c.department
           where c.id in (select a.complaintId from ComplaintAssignment a where a.fieldOfficer.id = :officerId and a.active = true)
             and (:status is null or c.status = :status)
           """,
           countQuery = """
           select count(c) from Complaint c
           where c.id in (select a.complaintId from ComplaintAssignment a where a.fieldOfficer.id = :officerId and a.active = true)
             and (:status is null or c.status = :status)
           """)
    Page<Complaint> findAssignedTo(@Param("officerId") Long officerId, @Param("status") ComplaintStatus status, Pageable pageable);

    // ---- scheduler queries -----------------------------------------------------------
    @Query("""
           select c from Complaint c join fetch c.department left join c.duplicateGroup g
           where c.status in :statuses and c.slaDueAt is not null and c.slaWarnedAt is null
             and (g is null or g.masterComplaintId = c.id)
           """)
    List<Complaint> findSlaWarningCandidates(@Param("statuses") Collection<ComplaintStatus> statuses);

    @Query("""
           select c from Complaint c join fetch c.department left join c.duplicateGroup g
           where c.status in :statuses and c.slaDueAt < :now and c.escalationLevel < :belowLevel
             and (g is null or g.masterComplaintId = c.id)
           """)
    List<Complaint> findOverdue(@Param("statuses") Collection<ComplaintStatus> statuses, @Param("now") Instant now,
                                @Param("belowLevel") int belowLevel);

    @Query("select c from Complaint c where c.status in :statuses and c.updatedAt < :before and c.aiAttempts < :maxAttempts order by c.id")
    List<Complaint> findWaitingForAi(@Param("statuses") Collection<ComplaintStatus> statuses, @Param("before") Instant before,
                                     @Param("maxAttempts") int maxAttempts);

    @Query("select c from Complaint c where c.status = com.jansunwai.entity.ComplaintStatus.RESOLVED and c.resolvedAt < :cutoff")
    List<Complaint> findResolvedBefore(@Param("cutoff") Instant cutoff);

    @Query("select c from Complaint c where c.duplicateGroup.id = :groupId and c.id <> :exceptId")
    List<Complaint> findGroupMembers(@Param("groupId") Long groupId, @Param("exceptId") Long exceptId);
}
