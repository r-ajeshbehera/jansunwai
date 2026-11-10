package com.jansunwai.repository;

import com.jansunwai.entity.ComplaintAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ComplaintAssignmentRepository extends JpaRepository<ComplaintAssignment, Long> {
    @Query("select a from ComplaintAssignment a join fetch a.fieldOfficer where a.complaintId = :complaintId and a.active = true")
    Optional<ComplaintAssignment> findActive(@Param("complaintId") Long complaintId);

    @Query("select a from ComplaintAssignment a join fetch a.fieldOfficer where a.complaintId = :complaintId order by a.assignedAt")
    List<ComplaintAssignment> findHistory(@Param("complaintId") Long complaintId);
}
