package com.jansunwai.service;

import com.jansunwai.dto.request.RemarkRequest;
import com.jansunwai.dto.response.RemarkResponse;
import com.jansunwai.entity.*;
import com.jansunwai.exception.BusinessRuleException;
import com.jansunwai.exception.ResourceNotFoundException;
import com.jansunwai.notification.NotificationService;
import com.jansunwai.repository.ComplaintAssignmentRepository;
import com.jansunwai.repository.ComplaintRemarkRepository;
import com.jansunwai.repository.ComplaintRepository;
import com.jansunwai.repository.UserRepository;
import com.jansunwai.security.AccessPolicy;
import com.jansunwai.security.AppUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remarks: INTERNAL (officers only) or PUBLIC (the citizen sees them). An officer can "request more information",
 * which is always public and notifies the citizen; the citizen answers with a public remark.
 */
@Service
public class RemarkService {
    private final ComplaintRepository complaints;
    private final ComplaintRemarkRepository remarks;
    private final ComplaintAssignmentRepository assignments;
    private final UserRepository users;
    private final AccessPolicy access;
    private final NotificationService notifications;
    private final AuditService audit;

    public RemarkService(ComplaintRepository complaints, ComplaintRemarkRepository remarks,
                         ComplaintAssignmentRepository assignments, UserRepository users, AccessPolicy access,
                         NotificationService notifications, AuditService audit) {
        this.complaints = complaints;
        this.remarks = remarks;
        this.assignments = assignments;
        this.users = users;
        this.access = access;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Transactional
    public RemarkResponse add(Long complaintId, RemarkRequest req, AppUserPrincipal user) {
        Complaint c = complaints.findWithDetails(complaintId).orElseThrow(() -> new ResourceNotFoundException("Complaint", complaintId));
        access.assertCanView(user, c);
        if (c.getStatus().isFinal()) throw new BusinessRuleException("COMPLAINT_CLOSED", "This complaint is closed");
        RemarkVisibility visibility = req.visibility();
        boolean infoRequest = req.infoRequest();
        if (user.role() == Role.CITIZEN) {
            access.assertOwner(user, c);
            visibility = RemarkVisibility.PUBLIC;          // citizens cannot write hidden notes
            infoRequest = false;
        } else if (infoRequest) {
            visibility = RemarkVisibility.PUBLIC;
        }
        User author = users.getReferenceById(user.id());
        ComplaintRemark r = remarks.save(new ComplaintRemark(c.getId(), author, visibility, infoRequest, req.text().trim()));

        if (infoRequest) {
            notifications.notify(c.getCitizenId(), c.getId(), "INFO_REQUESTED",
                    "The municipality needs more information about " + c.getTrackingId() + ": " + req.text().trim());
        } else if (user.role() == Role.CITIZEN) {
            assignments.findActive(c.getId()).ifPresentOrElse(
                    a -> notifications.notify(a.getFieldOfficer().getId(), c.getId(), "CITIZEN_REPLY",
                            "The citizen added information to " + c.getTrackingId()),
                    () -> notifications.notifyDepartmentOfficers(c.getDepartmentId(), c.getId(), "CITIZEN_REPLY",
                            "The citizen added information to " + c.getTrackingId()));
        } else if (visibility == RemarkVisibility.PUBLIC) {
            notifications.notify(c.getCitizenId(), c.getId(), "UPDATE", "Update on " + c.getTrackingId() + ": " + req.text().trim());
        }
        audit.log(user.id(), "REMARK_ADDED", "COMPLAINT", c.getId(), visibility.name());
        return new RemarkResponse(r.getId(), user.fullName(), visibility, infoRequest, r.getText(), r.getCreatedAt());
    }
}
