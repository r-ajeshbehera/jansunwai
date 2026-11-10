package com.jansunwai.service;

import com.jansunwai.config.AppProperties;
import com.jansunwai.dto.response.AttachmentResponse;
import com.jansunwai.entity.*;
import com.jansunwai.exception.BusinessRuleException;
import com.jansunwai.exception.ResourceNotFoundException;
import com.jansunwai.repository.ComplaintAttachmentRepository;
import com.jansunwai.repository.ComplaintRepository;
import com.jansunwai.security.AccessPolicy;
import com.jansunwai.security.AppUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Photos: citizens add EVIDENCE to their own open complaint; the assigned field officer adds RESOLUTION photos.
 * Limits: image types by magic bytes, max size, max files per complaint. Files are never public.
 */
@Service
public class AttachmentService {
    private final ComplaintRepository complaints;
    private final ComplaintAttachmentRepository attachments;
    private final FileStorageService storage;
    private final AccessPolicy access;
    private final AuditService audit;
    private final long maxBytes;
    private final int maxPerComplaint;

    public AttachmentService(ComplaintRepository complaints, ComplaintAttachmentRepository attachments,
                             FileStorageService storage, AccessPolicy access, AuditService audit, AppProperties props) {
        this.complaints = complaints;
        this.attachments = attachments;
        this.storage = storage;
        this.access = access;
        this.audit = audit;
        this.maxBytes = props.uploads().maxBytes();
        this.maxPerComplaint = props.uploads().maxPerComplaint();
    }

    public record FileDownload(InputStream stream, String contentType, String fileName, long size) { }

    @Transactional
    public AttachmentResponse upload(Long complaintId, MultipartFile file, AppUserPrincipal user) {
        Complaint c = complaints.findWithDetails(complaintId).orElseThrow(() -> new ResourceNotFoundException("Complaint", complaintId));
        AttachmentKind kind;
        if (user.role() == Role.CITIZEN) {
            access.assertOwner(user, c);
            if (c.getStatus().isFinal() || c.getStatus() == ComplaintStatus.RESOLVED) {
                throw new BusinessRuleException("COMPLAINT_CLOSED", "Photos cannot be added to a resolved or closed complaint");
            }
            kind = AttachmentKind.EVIDENCE;
        } else {
            access.assertAssignedFieldOfficer(user, c);
            if (c.getStatus() != ComplaintStatus.IN_PROGRESS && c.getStatus() != ComplaintStatus.ASSIGNED) {
                throw new BusinessRuleException("NOT_IN_PROGRESS", "Resolution photos can be added while the work is in progress");
            }
            kind = AttachmentKind.RESOLUTION;
        }
        if (file == null || file.isEmpty()) throw new BusinessRuleException("FILE_REQUIRED", "Choose a photo to upload");
        if (file.getSize() > maxBytes) throw new BusinessRuleException("FILE_TOO_LARGE", "The photo is larger than " + (maxBytes / 1024 / 1024) + " MB");
        if (attachments.countByComplaintId(c.getId()) >= maxPerComplaint) {
            throw new BusinessRuleException("TOO_MANY_FILES", "A complaint can have at most " + maxPerComplaint + " photos");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessRuleException("UPLOAD_FAILED", "The file could not be read");
        }
        FileStorageService.Stored stored = storage.store(bytes);   // validates magic bytes first
        try {
            ComplaintAttachment a = attachments.save(new ComplaintAttachment(c.getId(), kind, stored.storedName(),
                    safeName(file.getOriginalFilename()), stored.contentType(), bytes.length, user.id()));
            audit.log(user.id(), "ATTACHMENT_UPLOADED", "COMPLAINT", c.getId(), kind.name());
            return AttachmentResponse.from(a);
        } catch (RuntimeException e) {
            storage.deleteQuietly(stored.storedName());           // no orphan files if the DB insert fails
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public FileDownload download(Long complaintId, Long attachmentId, AppUserPrincipal user) throws IOException {
        Complaint c = complaints.findWithDetails(complaintId).orElseThrow(() -> new ResourceNotFoundException("Complaint", complaintId));
        access.assertCanView(user, c);
        ComplaintAttachment a = attachments.findById(attachmentId)
                .filter(x -> x.getComplaintId().equals(c.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));
        return new FileDownload(storage.open(a.getStoredName()), a.getContentType(), a.getOriginalName(), a.getSizeBytes());
    }

    static String safeName(String original) {
        if (original == null || original.isBlank()) return "photo";
        String n = original.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return n.length() > 150 ? n.substring(n.length() - 150) : n;
    }
}
