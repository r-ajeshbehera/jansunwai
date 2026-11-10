package com.jansunwai.repository;

import com.jansunwai.entity.ComplaintStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ComplaintStatusHistoryRepository extends JpaRepository<ComplaintStatusHistory, Long> {
    @Query("select h from ComplaintStatusHistory h left join fetch h.changedBy where h.complaintId = :id order by h.createdAt, h.id")
    List<ComplaintStatusHistory> findTimeline(@Param("id") Long complaintId);
}
