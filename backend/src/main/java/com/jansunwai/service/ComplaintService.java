package com.jansunwai.service;

import com.jansunwai.ai.LanguageDetector;
import com.jansunwai.dto.request.CreateComplaintRequest;
import com.jansunwai.dto.response.ComplaintDetail;
import com.jansunwai.dto.response.ComplaintSummary;
import com.jansunwai.dto.response.PageResponse;
import com.jansunwai.dto.response.PublicTrackingResponse;
import com.jansunwai.entity.*;
import com.jansunwai.exception.BusinessRuleException;
import com.jansunwai.exception.ResourceNotFoundException;
import com.jansunwai.repository.*;
import com.jansunwai.security.AccessPolicy;
import com.jansunwai.security.AppUserPrincipal;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

@Service
public class ComplaintService {
    static final EnumSet<ComplaintStatus> SLA_STATUSES = EnumSet.of(ComplaintStatus.APPROVED, ComplaintStatus.ASSIGNED,
            ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLUTION_SUBMITTED, ComplaintStatus.REOPENED);

    private final ComplaintRepository complaints;
    private final UserRepository users;
    private final WardRepository wards;
    private final GeoRepository geo;
    private final ComplaintStatusHistoryRepository history;
    private final TrackingIdGenerator trackingIds;
    private final WorkflowService workflow;
    private final ComplaintViewMapper views;
    private final AccessPolicy access;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public ComplaintService(ComplaintRepository complaints, UserRepository users, WardRepository wards, GeoRepository geo,
                            ComplaintStatusHistoryRepository history, TrackingIdGenerator trackingIds,
                            WorkflowService workflow, ComplaintViewMapper views, AccessPolicy access, AuditService audit,
                            ApplicationEventPublisher events) {
        this.complaints = complaints;
        this.users = users;
        this.wards = wards;
        this.geo = geo;
        this.history = history;
        this.trackingIds = trackingIds;
        this.workflow = workflow;
        this.views = views;
        this.access = access;
        this.audit = audit;
        this.events = events;
    }

    /**
     * Save the complaint exactly as written, find the ward from GPS if needed, record history, and hand it to the
     * AI pipeline AFTER the transaction commits. The citizen gets the tracking ID immediately.
     */
    @Transactional
    public ComplaintDetail submit(CreateComplaintRequest req, AppUserPrincipal citizen) {
        if ((req.latitude() == null) != (req.longitude() == null)) {
            throw new BusinessRuleException("LOCATION_INCOMPLETE", "Send both latitude and longitude, or neither");
        }
        String language = req.language() != null ? req.language() : LanguageDetector.detect(req.title() + " " + req.description());
        Ward ward = null;
        if (req.wardId() != null) {
            ward = wards.findById(req.wardId()).orElseThrow(() -> new ResourceNotFoundException("Ward", req.wardId()));
        } else if (req.latitude() != null) {
            ward = geo.wardContaining(req.latitude(), req.longitude()).flatMap(wards::findById).orElse(null);
        }
        User me = users.getReferenceById(citizen.id());
        Complaint c = complaints.save(new Complaint(trackingIds.next(), me, req.title().trim(), req.description().trim(),
                language, ward, req.latitude(), req.longitude(), req.landmark() == null ? null : req.landmark().trim()));
        history.save(new ComplaintStatusHistory(c.getId(), null, ComplaintStatus.SUBMITTED,
                "Complaint received. Tracking ID " + c.getTrackingId(), true, me));
        workflow.record(c, ComplaintStatus.AI_ANALYSIS_PENDING, "Automatic analysis started", false, null);
        complaints.saveAndFlush(c);
        audit.log(citizen.id(), "COMPLAINT_SUBMITTED", "COMPLAINT", c.getId(), c.getTrackingId());
        events.publishEvent(new ComplaintSubmittedEvent(c.getId()));
        return views.toDetail(c, citizen);
    }

    /** Role-based list: citizen = own, field officer = assigned, department officer = own department + unrouted. */
    @Transactional(readOnly = true)
    public PageResponse<ComplaintSummary> list(AppUserPrincipal user, ComplaintStatus status, Long departmentId, Long wardId,
                                               boolean overdueOnly, String q, Pageable pageable) {
        String query = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        return switch (user.role()) {
            case CITIZEN -> PageResponse.of(complaints.findForCitizen(user.id(), status, pageable), ComplaintSummary::from);
            case FIELD_OFFICER -> PageResponse.of(complaints.findAssignedTo(user.id(), status, pageable), ComplaintSummary::from);
            case DEPARTMENT_OFFICER -> PageResponse.of(complaints.searchForStaff(user.departmentId(), true, status, wardId,
                    overdueOnly, Instant.now(), SLA_STATUSES, query, pageable), ComplaintSummary::from);
            case ADMIN -> PageResponse.of(complaints.searchForStaff(departmentId, false, status, wardId, overdueOnly,
                    Instant.now(), SLA_STATUSES, query, pageable), ComplaintSummary::from);
        };
    }

    @Transactional(readOnly = true)
    public ComplaintDetail get(Long id, AppUserPrincipal user) {
        Complaint c = complaints.findWithDetails(id).orElseThrow(() -> new ResourceNotFoundException("Complaint", id));
        access.assertCanView(user, c);
        return views.toDetail(c, user);
    }

    /** Public tracking: anyone with the tracking ID; only non-personal fields and public notes. */
    @Transactional(readOnly = true)
    public PublicTrackingResponse track(String trackingId) {
        Complaint c = complaints.findByTrackingId(trackingId.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("Complaint", trackingId));
        String linkedTo = null;
        if (c.getDuplicateGroup() != null && !c.getDuplicateGroup().getMasterComplaintId().equals(c.getId())) {
            linkedTo = complaints.findById(c.getDuplicateGroup().getMasterComplaintId()).map(Complaint::getTrackingId).orElse(null);
        }
        List<PublicTrackingResponse.PublicEvent> events = history.findTimeline(c.getId()).stream()
                .filter(ComplaintStatusHistory::isPublicNote)
                .map(h -> new PublicTrackingResponse.PublicEvent(h.getToStatus().name(), publicNote(h), h.getCreatedAt()))
                .toList();
        return new PublicTrackingResponse(c.getTrackingId(), c.getCategory() == null ? "Being classified" : c.getCategory().label(),
                c.getStatus().name(), c.getWard() == null ? null : c.getWard().getName(),
                c.getDepartment() == null || c.getClassificationSource() == ClassificationSource.AI_SUGGESTED ? null
                        : c.getDepartment().getName(),
                c.getCreatedAt(), c.getSlaDueAt(), ComplaintSummary.isOverdue(c), linkedTo, events);
    }

    /** Public notes may still contain an officer's free text; the tracking page shows generic wording instead. */
    private static String publicNote(ComplaintStatusHistory h) {
        return switch (h.getToStatus()) {
            case SUBMITTED -> "Complaint received";
            case UNDER_REVIEW -> "Under review by the municipality";
            case APPROVED -> "Accepted by the responsible department";
            case ASSIGNED -> "Assigned to a field team";
            case IN_PROGRESS -> "Work in progress";
            case RESOLVED -> "Resolved";
            case CLOSED -> "Closed";
            case REJECTED -> "Not accepted";
            case REOPENED -> "Reopened by the citizen";
            default -> h.getFromStatus() == h.getToStatus() ? "Update" : h.getToStatus().name();
        };
    }
}
