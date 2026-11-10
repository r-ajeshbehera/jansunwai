package com.jansunwai.repository;

import com.jansunwai.entity.CandidateStatus;
import com.jansunwai.entity.DuplicateCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DuplicateCandidateRepository extends JpaRepository<DuplicateCandidate, Long> {
    boolean existsByComplaint_IdAndCandidate_Id(Long complaintId, Long candidateId);

    @Query("""
           select dc from DuplicateCandidate dc join fetch dc.complaint c join fetch dc.candidate o
           left join fetch c.department left join fetch o.department
           where dc.id = :id
           """)
    Optional<DuplicateCandidate> findWithComplaints(@Param("id") Long id);

    @Query("""
           select dc from DuplicateCandidate dc join fetch dc.complaint c join fetch dc.candidate o
           where c.id = :complaintId order by dc.totalScore desc
           """)
    List<DuplicateCandidate> findForComplaint(@Param("complaintId") Long complaintId);

    /** Pending suggestions for a department (null = all). Either complaint belonging to the department counts. */
    @Query("""
           select dc from DuplicateCandidate dc join fetch dc.complaint c join fetch dc.candidate o
           left join fetch c.department cd left join fetch o.department od
           where dc.status = :status
             and (:departmentId is null or cd.id = :departmentId or od.id = :departmentId or cd.id is null)
           order by dc.totalScore desc
           """)
    List<DuplicateCandidate> findByStatusForDepartment(@Param("status") CandidateStatus status,
                                                       @Param("departmentId") Long departmentId);
}
