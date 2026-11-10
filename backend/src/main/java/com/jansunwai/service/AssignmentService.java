package com.jansunwai.service;

import com.jansunwai.dto.request.AssignRequest;
import com.jansunwai.dto.response.ComplaintDetail;
import com.jansunwai.dto.response.UserResponse;
import com.jansunwai.entity.*;
import com.jansunwai.exception.BusinessRuleException;
import com.jansunwai.exception.InvalidStateException;
import com.jansunwai.exception.ResourceNotFoundException;
import com.jansunwai.notification.NotificationService;
import com.jansunwai.repository.ComplaintAssignmentRepository;
import com.jansunwai.repository.ComplaintRepository;
import com.jansunwai.repository.UserRepository;
import com.jansunwai.security.AccessPolicy;
import com.jansunwai.security.AppUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;

/**
 * Assign / reassign a field officer. Concurrency (two officers at the same moment) is handled three ways:
 *  1. state check: an ASSIGNED complaint can only be changed with reassign=true,
 *  2. @Version on Complaint: the complaint row is updated FIRST, so the slower request fails with 409,
 *  3. DB partial unique index: at most one active assignment per complaint.
 */
@Service
public class AssignmentService {
    private static final EnumSet<ComplaintStatus> FIRST_ASSIGN = EnumSet.of(ComplaintStatus.APPROVED, ComplaintStatus.REOPENED);
    private static final EnumSet<ComplaintStatus> REASSIGN = EnumSet.of(ComplaintStatus.ASSIGNED, ComplaintStatus.IN_PROGRESS);

    private final ComplaintRepository complaints;
    private final ComplaintAssignmentRepository assignments;
    private final UserRepository users;
    private final WorkflowService workflow;
    private final AccessPolicy access;
    private final NotificationService notifications;
    private final AuditService audit;
    private final ComplaintViewMapper views;

    public AssignmentService(ComplaintRepository complaints, ComplaintAssignmentRepository assignments, UserRepository users,
                             WorkflowService workflow, AccessPolicy access, NotificationService notifications,
                             AuditService audit, ComplaintViewMapper views) {
        this.complaints = complaints;
        this.assignments = assignments;
        this.users = users;
        this.workflow = workflow;
        this.access = access;
        this.notifications = notifications;
        this.audit = audit;
        this.views = views;
    }

    @Transactional
    public ComplaintDetail assign(Long complaintId, AssignRequest req, AppUserPrincipal officer) {
        Complaint c = complaints.findWithDetails(complaintId).orElseThrow(() -> new ResourceNotFoundException("Complaint", complaintId));
        access.assertDepartmentOfficer(officer, c);
        if (c.getDepartmentId() == null) throw new BusinessRuleException("NOT_ROUTED", "Approve the complaint for a department first");
        if (c.isLinkedDuplicate()) {
            throw new BusinessRuleException("LINKED_DUPLICATE", "This complaint is linked to another one; assign the master complaint");
        }
        boolean reassigning = REASSIGN.contains(c.getStatus());
        if (reassigning && !req.reassign()) {
            throw new InvalidStateException("Complaint " + c.getTrackingId() + " is already assigned. Use reassign to change the officer.");
        }
        if (!reassigning && !FIRST_ASSIGN.contains(c.getStatus())) {
            throw new InvalidStateException("Complaint " + c.getTrackingId() + " cannot be assigned in status " + c.getStatus());
        }
        User field = users.findWithDepartment(req.fieldOfficerId())
                .orElseThrow(() -> new ResourceNotFoundException("User", req.fieldOfficerId()));
        if (field.getRole() != Role.FIELD_OFFICER || !field.isActive() || !c.getDepartmentId().equals(field.getDepartmentId())) {
            throw new BusinessRuleException("INVALID_FIELD_OFFICER", "Choose an active field officer of " + c.getDepartment().getName());
        }
        User me = users.getReferenceById(officer.id());

        assignments.findActive(c.getId()).ifPresent(old -> {
            if (old.getFieldOfficer().getId().equals(field.getId())) {
                throw new BusinessRuleException("ALREADY_ASSIGNED", "This officer is already assigned");
            }
            old.end();
            assignments.saveAndFlush(old);
            notifications.notify(old.getFieldOfficer().getId(), c.getId(), "UNASSIGNED",
                    "Complaint " + c.getTrackingId() + " was reassigned to another officer.");
        });
        if (reassigning) {
            workflow.note(c, "Reassigned to " + field.getFullName() + (req.note() == null ? "" : ": " + req.note()), false, me);
        } else {
            workflow.record(c, ComplaintStatus.ASSIGNED, "Assigned to a field team", true, me);
        }
        complaints.saveAndFlush(c);                      // version check FIRST: the slower concurrent request fails here
        assignments.saveAndFlush(new ComplaintAssignment(c.getId(), field, officer.id(), req.note()));
        notifications.notify(field.getId(), c.getId(), "ASSIGNED", "You have been assigned complaint " + c.getTrackingId()
                + " (" + (c.getPriority() == null ? "" : c.getPriority() + " priority") + ").");
        audit.log(officer.id(), reassigning ? "COMPLAINT_REASSIGNED" : "COMPLAINT_ASSIGNED", "COMPLAINT", c.getId(),
                "fieldOfficer=" + field.getId());
        return views.toDetail(c, officer);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> fieldOfficers(Long departmentId, AppUserPrincipal officer) {
        Long dept = officer.isAdmin() ? departmentId : officer.departmentId();
        if (dept == null) throw new BusinessRuleException("DEPARTMENT_REQUIRED", "departmentId is required");
        return users.findByDepartment_IdAndRoleAndActiveTrueOrderByFullName(dept, Role.FIELD_OFFICER).stream()
                .map(UserResponse::from).toList();
    }
}
