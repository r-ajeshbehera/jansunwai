package com.jansunwai.service;

import com.jansunwai.dto.request.ReviewRequest;
import com.jansunwai.dto.request.StatusChangeRequest;
import com.jansunwai.dto.response.ComplaintDetail;
import com.jansunwai.entity.*;
import com.jansunwai.exception.BusinessRuleException;
import com.jansunwai.exception.ResourceNotFoundException;
import com.jansunwai.notification.NotificationService;
import com.jansunwai.repository.*;
import com.jansunwai.security.AccessPolicy;
import com.jansunwai.security.AppUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Status changes, with WHO may make each one. Every change writes complaint_status_history.
 * Which moves exist at all is defined in ComplaintStatus.allowedNext(); this class adds the role rules.
 */
@Service
public class WorkflowService {
    static final DateTimeFormatter DUE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a").withZone(ZoneId.of("Asia/Kolkata"));

    private final ComplaintRepository complaints;
    private final ComplaintStatusHistoryRepository history;
    private final ComplaintAiAnalysisRepository analyses;
    private final ComplaintAttachmentRepository attachments;
    private final ComplaintAssignmentRepository assignments;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final SlaService sla;
    private final AccessPolicy access;
    private final NotificationService notifications;
    private final AuditService audit;
    private final ComplaintViewMapper views;
    private final int autoCloseDays;

    public WorkflowService(ComplaintRepository complaints, ComplaintStatusHistoryRepository history,
                           ComplaintAiAnalysisRepository analyses, ComplaintAttachmentRepository attachments,
                           ComplaintAssignmentRepository assignments,
                           DepartmentRepository departments, UserRepository users, SlaService sla, AccessPolicy access,
                           NotificationService notifications, AuditService audit, ComplaintViewMapper views,
                           com.jansunwai.config.AppProperties props) {
        this.complaints = complaints;
        this.history = history;
        this.analyses = analyses;
        this.attachments = attachments;
        this.assignments = assignments;
        this.departments = departments;
        this.users = users;
        this.sla = sla;
        this.access = access;
        this.notifications = notifications;
        this.audit = audit;
        this.views = views;
        this.autoCloseDays = props.sla().autoCloseDays();
    }

    /** Low-level helper used by every service: validated move + history row. actor null = system. */
    public ComplaintStatus record(Complaint c, ComplaintStatus next, String note, boolean publicNote, User actor) {
        ComplaintStatus from = c.moveTo(next);
        history.save(new ComplaintStatusHistory(c.getId(), from, next, note, publicNote, actor));
        return from;
    }

    /** A history row without a status change (re-routing, reassignment, escalation). */
    public void note(Complaint c, String note, boolean publicNote, User actor) {
        history.save(new ComplaintStatusHistory(c.getId(), c.getStatus(), c.getStatus(), note, publicNote, actor));
    }

    // ---- department officer review --------------------------------------------------------

    @Transactional
    public ComplaintDetail review(Long id, ReviewRequest req, AppUserPrincipal officer) {
        Complaint c = load(id);
        access.assertDepartmentOfficer(officer, c);
        if (c.getStatus() != ComplaintStatus.UNDER_REVIEW) {
            throw new com.jansunwai.exception.InvalidStateException("Only complaints UNDER_REVIEW can be reviewed (current: " + c.getStatus() + ")");
        }
        User me = users.getReferenceById(officer.id());
        switch (req.decision()) {
            case APPROVE -> approve(c, req, officer, me);
            case REROUTE -> reroute(c, req, officer, me);
            case REJECT -> {
                if (req.reason() == null || req.reason().isBlank()) throw new BusinessRuleException("REASON_REQUIRED", "A rejection reason is required");
                c.reject(req.reason().trim());
                record(c, ComplaintStatus.REJECTED, req.reason().trim(), true, me);
                complaints.saveAndFlush(c);
                notifications.notify(c.getCitizenId(), c.getId(), "REJECTED", "Complaint " + c.getTrackingId()
                        + " was not accepted: " + req.reason().trim());
                audit.log(officer.id(), "COMPLAINT_REJECTED", "COMPLAINT", c.getId(), null);
            }
        }
        return views.toDetail(c, officer);
    }

    private void approve(Complaint c, ReviewRequest req, AppUserPrincipal officer, User me) {
        if (req.category() == null || req.departmentId() == null || req.priority() == null) {
            throw new BusinessRuleException("CLASSIFICATION_REQUIRED", "category, departmentId and priority are required to approve");
        }
        if (!officer.isAdmin() && !req.departmentId().equals(officer.departmentId())) {
            throw new BusinessRuleException("WRONG_DEPARTMENT", "You can only accept complaints for your own department. Use REROUTE.");
        }
        Department dept = departments.findById(req.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department", req.departmentId()));
        boolean corrected = analyses.findFirstByComplaintIdAndStatusOrderByCreatedAtDesc(c.getId(), AiStatus.SUCCEEDED)
                .map(a -> a.getCategory() != req.category() || a.getPriority() != req.priority()
                        || !Objects.equals(a.getDepartmentCode(), dept.getCode()))
                .orElse(true);
        SlaService.Policy policy = sla.policyFor(dept.getId(), req.priority());
        Instant due = SlaService.dueAt(Instant.now(), policy.resolutionHours());
        c.approveClassification(req.category(), dept, req.priority(), me, corrected, due);
        record(c, ComplaintStatus.APPROVED, "Accepted by " + dept.getName() + ". Expected resolution by "
                + DUE_FORMAT.format(due) + (req.note() == null ? "" : ". " + req.note()), true, me);
        complaints.saveAndFlush(c);
        notifications.notify(c.getCitizenId(), c.getId(), "APPROVED", "Complaint " + c.getTrackingId() + " was accepted by "
                + dept.getName() + ". Expected resolution by " + DUE_FORMAT.format(due) + ".");
        audit.log(officer.id(), corrected ? "CLASSIFICATION_CORRECTED" : "CLASSIFICATION_CONFIRMED", "COMPLAINT", c.getId(),
                req.category() + "/" + dept.getCode() + "/" + req.priority());
        if (c.getDuplicateGroup() != null && c.getDuplicateGroup().getMasterComplaintId().equals(c.getId())) {
            for (Complaint member : complaints.findGroupMembers(c.getDuplicateGroup().getId(), c.getId())) {
                acceptWithMaster(member, c, me);
            }
        }
    }

    private void reroute(Complaint c, ReviewRequest req, AppUserPrincipal officer, User me) {
        if (req.departmentId() == null) throw new BusinessRuleException("DEPARTMENT_REQUIRED", "departmentId is required to reroute");
        Department dept = departments.findById(req.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department", req.departmentId()));
        if (dept.getId().equals(c.getDepartmentId())) throw new BusinessRuleException("SAME_DEPARTMENT", "Complaint is already with this department");
        String from = c.getDepartment() == null ? "unassigned" : c.getDepartment().getName();
        c.reroute(dept);
        note(c, "Rerouted from " + from + " to " + dept.getName() + (req.note() == null ? "" : ": " + req.note()), false, me);
        complaints.saveAndFlush(c);
        notifications.notifyDepartmentOfficers(dept.getId(), c.getId(), "REROUTED",
                "Complaint " + c.getTrackingId() + " was routed to your department for review.");
        audit.log(officer.id(), "COMPLAINT_REROUTED", "COMPLAINT", c.getId(), from + " -> " + dept.getCode());
    }

    /** A linked duplicate takes over the master's approved classification and deadline. */
    void acceptWithMaster(Complaint member, Complaint master, User actor) {
        if (member.getStatus() != ComplaintStatus.UNDER_REVIEW) return;
        member.approveClassification(master.getCategory(), master.getDepartment(), master.getPriority(), actor, false,
                master.getSlaDueAt());
        record(member, ComplaintStatus.APPROVED, "Accepted together with linked complaint " + master.getTrackingId(), true, actor);
        complaints.saveAndFlush(member);
        notifications.notify(member.getCitizenId(), member.getId(), "APPROVED", "Complaint " + member.getTrackingId()
                + " was accepted together with " + master.getTrackingId() + " (same issue).");
    }

    // ---- generic status changes (field officer, department officer, citizen) ----------------

    @Transactional
    public ComplaintDetail changeStatus(Long id, StatusChangeRequest req, AppUserPrincipal user) {
        Complaint c = load(id);
        access.assertCanView(user, c);
        User me = users.getReferenceById(user.id());
        ComplaintStatus target = req.status();
        String note = req.note() == null ? null : req.note().trim();
        switch (target) {
            case IN_PROGRESS -> {
                if (c.getStatus() == ComplaintStatus.RESOLUTION_SUBMITTED) {          // sent back by the department
                    access.assertDepartmentOfficer(user, c);
                    requireNote(note, "Explain what is still missing");
                    record(c, target, "Sent back for more work: " + note, false, me);
                    notifyAssignedOfficer(c, "Complaint " + c.getTrackingId() + " was sent back: " + note);
                } else {
                    access.assertAssignedFieldOfficer(user, c);
                    record(c, target, note == null ? "Work started" : note, true, me);
                    notifications.notify(c.getCitizenId(), c.getId(), "IN_PROGRESS",
                            "Work has started on your complaint " + c.getTrackingId() + ".");
                }
            }
            case RESOLUTION_SUBMITTED -> {
                access.assertAssignedFieldOfficer(user, c);
                requireNote(note, "Describe the work done");
                if (attachments.countByComplaintIdAndKind(c.getId(), AttachmentKind.RESOLUTION) == 0) {
                    throw new BusinessRuleException("EVIDENCE_REQUIRED", "Upload at least one photo of the completed work first");
                }
                record(c, target, note, false, me);
                notifications.notifyDepartmentOfficers(c.getDepartmentId(), c.getId(), "RESOLUTION_SUBMITTED",
                        "Work on " + c.getTrackingId() + " is complete and waiting for your approval.");
            }
            case RESOLVED -> {
                access.assertDepartmentOfficer(user, c);
                record(c, target, note == null ? "Issue resolved" : note, true, me);
                complaints.saveAndFlush(c);
                notifications.notify(c.getCitizenId(), c.getId(), "RESOLVED", "Complaint " + c.getTrackingId()
                        + " was resolved. Please confirm, or reopen it within " + autoCloseDays + " days if the problem remains.");
                resolveLinkedDuplicates(c, me);
            }
            case CLOSED -> {
                if (!user.isAdmin()) access.assertOwner(user, c);
                record(c, target, note == null ? "Confirmed by citizen" : note, true, me);
            }
            case REOPENED -> {
                access.assertOwner(user, c);
                requireNote(note, "Tell us why the problem is not solved");
                if (c.getResolvedAt() != null && c.getResolvedAt().plus(Duration.ofDays(autoCloseDays)).isBefore(Instant.now())) {
                    throw new BusinessRuleException("REOPEN_WINDOW_PASSED", "Complaints can only be reopened within "
                            + autoCloseDays + " days of resolution");
                }
                record(c, target, note, true, me);
                notifications.notifyDepartmentOfficers(c.getDepartmentId(), c.getId(), "REOPENED",
                        "Citizen reopened " + c.getTrackingId() + ": " + note);
            }
            default -> throw new BusinessRuleException("USE_DEDICATED_ENDPOINT",
                    "Status " + target + " is set through review, assignment or the AI pipeline, not this endpoint");
        }
        complaints.saveAndFlush(c);
        audit.log(user.id(), "STATUS_" + target, "COMPLAINT", c.getId(), null);
        return views.toDetail(c, user);
    }

    /** Linked duplicates are resolved together with their master; each citizen is told. */
    void resolveLinkedDuplicates(Complaint master, User actor) {
        if (master.getDuplicateGroup() == null || !master.getDuplicateGroup().getMasterComplaintId().equals(master.getId())) return;
        List<Complaint> members = complaints.findGroupMembers(master.getDuplicateGroup().getId(), master.getId());
        for (Complaint m : members) {
            if (!m.getStatus().isSlaActive() && m.getStatus() != ComplaintStatus.UNDER_REVIEW) continue;
            if (m.getStatus() == ComplaintStatus.UNDER_REVIEW) {
                m.approveClassification(master.getCategory(), master.getDepartment(), master.getPriority(), actor, false,
                        master.getSlaDueAt());
            }
            ComplaintStatus from = m.resolveWithMaster();
            history.save(new ComplaintStatusHistory(m.getId(), from, ComplaintStatus.RESOLVED,
                    "Resolved together with linked complaint " + master.getTrackingId(), true, actor));
            complaints.saveAndFlush(m);
            notifications.notify(m.getCitizenId(), m.getId(), "RESOLVED", "Complaint " + m.getTrackingId()
                    + " was resolved together with " + master.getTrackingId() + ".");
        }
    }

    private void notifyAssignedOfficer(Complaint c, String message) {
        assignments.findActive(c.getId()).ifPresent(a ->
                notifications.notify(a.getFieldOfficer().getId(), c.getId(), "SENT_BACK", message));
    }

    private static void requireNote(String note, String hint) {
        if (note == null || note.isBlank()) throw new BusinessRuleException("NOTE_REQUIRED", hint);
    }

    private Complaint load(Long id) {
        return complaints.findWithDetails(id).orElseThrow(() -> new ResourceNotFoundException("Complaint", id));
    }
}
