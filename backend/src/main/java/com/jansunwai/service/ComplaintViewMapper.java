package com.jansunwai.service;

import com.jansunwai.dto.response.*;
import com.jansunwai.entity.*;
import com.jansunwai.repository.*;
import com.jansunwai.security.AppUserPrincipal;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the complaint view for ONE viewer. Privacy rules live here:
 *  - citizens see only public timeline notes and public remarks, role labels instead of staff names, no AI internals;
 *  - staff never see the citizen's phone or e-mail (only the name);
 *  - "allowedActions" tells the UI which buttons to show (the backend still re-checks every action).
 */
@Component
public class ComplaintViewMapper {
    private final ComplaintStatusHistoryRepository history;
    private final ComplaintRemarkRepository remarks;
    private final ComplaintAttachmentRepository attachments;
    private final ComplaintAssignmentRepository assignments;
    private final ComplaintAiAnalysisRepository analyses;
    private final ComplaintRepository complaints;

    public ComplaintViewMapper(ComplaintStatusHistoryRepository history, ComplaintRemarkRepository remarks,
                               ComplaintAttachmentRepository attachments, ComplaintAssignmentRepository assignments,
                               ComplaintAiAnalysisRepository analyses, ComplaintRepository complaints) {
        this.history = history;
        this.remarks = remarks;
        this.attachments = attachments;
        this.assignments = assignments;
        this.analyses = analyses;
        this.complaints = complaints;
    }

    public ComplaintDetail toDetail(Complaint c, AppUserPrincipal viewer) {
        boolean staff = viewer.role() != Role.CITIZEN;
        boolean owner = c.getCitizenId().equals(viewer.id());

        List<TimelineEntry> timeline = history.findTimeline(c.getId()).stream()
                .filter(h -> staff || h.isPublicNote())
                .map(h -> new TimelineEntry(h.getFromStatus(), h.getToStatus(), h.getNote(), actorLabel(h.getChangedBy(), staff, c),
                        h.isPublicNote(), h.getCreatedAt()))
                .toList();
        List<RemarkResponse> remarkList = remarks.findForComplaint(c.getId()).stream()
                .filter(r -> staff || r.getVisibility() == RemarkVisibility.PUBLIC)
                .map(r -> new RemarkResponse(r.getId(), actorLabel(r.getAuthor(), staff, c), r.getVisibility(), r.isInfoRequest(),
                        r.getText(), r.getCreatedAt()))
                .toList();
        List<AttachmentResponse> files = attachments.findByComplaintIdOrderByCreatedAt(c.getId()).stream()
                .map(AttachmentResponse::from).toList();
        AssignmentResponse assignment = assignments.findActive(c.getId())
                .map(a -> new AssignmentResponse(staff ? a.getFieldOfficer().getId() : null,
                        staff ? a.getFieldOfficer().getFullName() : "Field team", staff ? a.getNote() : null, true,
                        a.getAssignedAt(), null))
                .orElse(null);
        AiAnalysisResponse ai = staff ? analyses.findByComplaintIdOrderByCreatedAtDesc(c.getId()).stream().findFirst()
                .map(AiAnalysisResponse::from).orElse(null) : null;

        ComplaintDetail.DuplicateInfo dup = null;
        if (c.getDuplicateGroup() != null) {
            DuplicateGroup g = c.getDuplicateGroup();
            boolean isMaster = g.getMasterComplaintId().equals(c.getId());
            String masterTracking = isMaster ? c.getTrackingId()
                    : complaints.findById(g.getMasterComplaintId()).map(Complaint::getTrackingId).orElse(null);
            List<String> linked = staff || isMaster
                    ? complaints.findGroupMembers(g.getId(), g.getMasterComplaintId()).stream().map(Complaint::getTrackingId).toList()
                    : List.of();
            dup = new ComplaintDetail.DuplicateInfo(masterTracking, isMaster, staff ? linked : List.of());
        }

        return new ComplaintDetail(c.getId(), c.getTrackingId(), c.getTitle(), c.getDescription(), c.getLanguage(),
                c.getStatus(), c.getCategory(), c.getPriority(), c.getClassificationSource(), c.getDepartmentId(),
                c.getDepartment() == null ? null : c.getDepartment().getName(),
                c.getWard() == null ? null : c.getWard().getId(), c.getWard() == null ? null : c.getWard().getName(),
                staff || owner ? c.getLatitude() : null, staff || owner ? c.getLongitude() : null,
                staff || owner ? c.getLandmark() : null, staff ? c.getCitizen().getFullName() : null,
                c.getCreatedAt(), c.getAcceptedAt(), c.getSlaDueAt(), ComplaintSummary.isOverdue(c), c.getEscalationLevel(),
                c.getResolvedAt(), c.getClosedAt(), c.getRejectionReason(), assignment, ai, dup, files, timeline, remarkList,
                allowedActions(c, viewer, owner, assignment));
    }

    private static String actorLabel(User u, boolean staffViewer, Complaint c) {
        if (u == null) return "System";
        if (u.getId().equals(c.getCitizenId())) return staffViewer ? u.getFullName() + " (citizen)" : "You";
        if (staffViewer) return u.getFullName();
        return switch (u.getRole()) {
            case FIELD_OFFICER -> "Field team";
            case DEPARTMENT_OFFICER -> "Department officer";
            case ADMIN -> "Municipal administration";
            case CITIZEN -> "Citizen";
        };
    }

    List<String> allowedActions(Complaint c, AppUserPrincipal v, boolean owner, AssignmentResponse assignment) {
        List<String> a = new ArrayList<>();
        ComplaintStatus s = c.getStatus();
        boolean linked = c.isLinkedDuplicate();
        switch (v.role()) {
            case CITIZEN -> {
                if (!owner) break;
                if (!s.isFinal() && s != ComplaintStatus.RESOLVED) a.add("ADD_PHOTO");
                if (s == ComplaintStatus.RESOLVED) { a.add("CONFIRM_CLOSE"); a.add("REOPEN"); }
                if (!s.isFinal()) a.add("ADD_REMARK");
            }
            case FIELD_OFFICER -> {
                if (assignment == null || !v.id().equals(assignment.fieldOfficerId())) break;
                if (s == ComplaintStatus.ASSIGNED) a.add("START_WORK");
                if (s == ComplaintStatus.IN_PROGRESS) { a.add("UPLOAD_RESOLUTION_PHOTO"); a.add("SUBMIT_RESOLUTION"); }
                a.add("ADD_REMARK");
            }
            case DEPARTMENT_OFFICER, ADMIN -> {
                if (s == ComplaintStatus.UNDER_REVIEW) { a.add("REVIEW"); a.add("RERUN_AI"); }
                if (!linked && (s == ComplaintStatus.APPROVED || s == ComplaintStatus.REOPENED)) a.add("ASSIGN");
                if (!linked && (s == ComplaintStatus.ASSIGNED || s == ComplaintStatus.IN_PROGRESS)) a.add("REASSIGN");
                if (s == ComplaintStatus.RESOLUTION_SUBMITTED) { a.add("APPROVE_RESOLUTION"); a.add("SEND_BACK"); }
                if (!s.isFinal() && c.getDuplicateGroup() == null) a.add("DETECT_DUPLICATES");
                if (v.isAdmin() && s == ComplaintStatus.RESOLVED) a.add("CONFIRM_CLOSE");
                a.add("ADD_REMARK");
            }
        }
        return a;
    }
}
