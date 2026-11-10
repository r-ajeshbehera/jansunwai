package com.jansunwai.repository;

import com.jansunwai.entity.AttachmentKind;
import com.jansunwai.entity.ComplaintAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintAttachmentRepository extends JpaRepository<ComplaintAttachment, Long> {
    List<ComplaintAttachment> findByComplaintIdOrderByCreatedAt(Long complaintId);

    long countByComplaintId(Long complaintId);

    long countByComplaintIdAndKind(Long complaintId, AttachmentKind kind);
}
