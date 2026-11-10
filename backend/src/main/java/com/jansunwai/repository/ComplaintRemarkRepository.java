package com.jansunwai.repository;

import com.jansunwai.entity.ComplaintRemark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ComplaintRemarkRepository extends JpaRepository<ComplaintRemark, Long> {
    @Query("select r from ComplaintRemark r join fetch r.author where r.complaintId = :id order by r.createdAt")
    List<ComplaintRemark> findForComplaint(@Param("id") Long complaintId);
}
