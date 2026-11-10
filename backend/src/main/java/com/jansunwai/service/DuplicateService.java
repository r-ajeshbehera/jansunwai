package com.jansunwai.service;

import com.jansunwai.config.AppProperties;
import com.jansunwai.dto.response.DuplicateCandidateResponse;
import com.jansunwai.entity.*;
import com.jansunwai.exception.BusinessRuleException;
import com.jansunwai.exception.InvalidStateException;
import com.jansunwai.exception.ResourceNotFoundException;
import com.jansunwai.notification.NotificationService;
import com.jansunwai.repository.*;
import com.jansunwai.security.AccessPolicy;
import com.jansunwai.security.AppUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Semantic duplicate detection: pgvector finds older complaints with similar meaning, PostGIS gives the distance,
 * DuplicateScorer combines them. Results are SUGGESTIONS. Linking happens only when an officer approves; each
 * citizen keeps their own tracking ID and history.
 */
@Service
public class DuplicateService {
    private final ComplaintRepository complaints;
    private final DuplicateCandidateRepository candidates;
    private final DuplicateGroupRepository groups;
    private final EmbeddingRepository embeddings;
    private final UserRepository users;
    private final WorkflowService workflow;
    private final AccessPolicy access;
    private final NotificationService notifications;
    private final AuditService audit;
    private final AppProperties.Duplicates cfg;

    public DuplicateService(ComplaintRepository complaints, DuplicateCandidateRepository candidates,
                            DuplicateGroupRepository groups, EmbeddingRepository embeddings, UserRepository users,
                            WorkflowService workflow, AccessPolicy access, NotificationService notifications,
                            AuditService audit, AppProperties props) {
        this.complaints = complaints;
        this.candidates = candidates;
        this.groups = groups;
        this.embeddings = embeddings;
        this.users = users;
        this.workflow = workflow;
        this.access = access;
        this.notifications = notifications;
        this.audit = audit;
        this.cfg = props.duplicates();
    }

    /** System use (AI pipeline). Idempotent: an existing pair is never inserted twice (UNIQUE(complaint, candidate)). */
    @Transactional
    public List<DuplicateCandidateResponse> detect(Long complaintId) {
        Complaint c = complaints.findWithDetails(complaintId).orElseThrow(() -> new ResourceNotFoundException("Complaint", complaintId));
        if (!embeddings.exists(complaintId)) return List.of();
        List<DuplicateCandidateResponse> out = new ArrayList<>();
        int created = 0;
        for (EmbeddingRepository.SimilarComplaint s : embeddings.findSimilarOlder(complaintId, cfg.windowDays(), cfg.maxCandidates() * 3)) {
            var score = DuplicateScorer.score(s.similarity(), s.distanceMeters(),
                    c.getCategory() == null ? null : c.getCategory().name(), s.category(), cfg);
            if (score.isEmpty()) continue;
            if (candidates.existsByComplaint_IdAndCandidate_Id(complaintId, s.complaintId())) continue;
            Complaint other = complaints.getReferenceById(s.complaintId());
            DuplicateCandidate dc = candidates.save(new DuplicateCandidate(c, other, BigDecimal.valueOf(score.get().semantic()),
                    s.distanceMeters(), BigDecimal.valueOf(score.get().total())));
            out.add(DuplicateCandidateResponse.from(candidates.findWithComplaints(dc.getId()).orElseThrow()));
            if (++created >= cfg.maxCandidates()) break;
        }
        if (created > 0) {
            notifications.notifyDepartmentOfficers(c.getDepartmentId(), c.getId(), "POSSIBLE_DUPLICATE",
                    "Complaint " + c.getTrackingId() + " may describe the same issue as " + created + " earlier complaint(s). Please review.");
        }
        return out;
    }

    /** Officer-triggered detection: returns new and existing suggestions for the complaint. */
    @Transactional
    public List<DuplicateCandidateResponse> detectFor(Long complaintId, AppUserPrincipal officer) {
        Complaint c = complaints.findWithDetails(complaintId).orElseThrow(() -> new ResourceNotFoundException("Complaint", complaintId));
        access.assertDepartmentOfficer(officer, c);
        detect(complaintId);
        return candidates.findForComplaint(complaintId).stream().map(DuplicateCandidateResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DuplicateCandidateResponse> pending(AppUserPrincipal officer) {
        Long dept = officer.isAdmin() ? null : officer.departmentId();
        return candidates.findByStatusForDepartment(CandidateStatus.PENDING, dept).stream()
                .map(DuplicateCandidateResponse::from).toList();
    }

    /**
     * Approve "same issue": the older complaint becomes (or stays) the master of a group, the newer one is linked.
     * The linked complaint follows the master: approved together, resolved together.
     */
    @Transactional
    public DuplicateCandidateResponse approve(Long candidateId, AppUserPrincipal officer) {
        DuplicateCandidate dc = candidates.findWithComplaints(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Duplicate suggestion", candidateId));
        Complaint child = complaints.findWithDetails(dc.getComplaint().getId()).orElseThrow();
        Complaint master = complaints.findWithDetails(dc.getCandidate().getId()).orElseThrow();
        access.assertDepartmentOfficer(officer, master);
        if (dc.getStatus() != CandidateStatus.PENDING) throw new InvalidStateException("This suggestion was already decided");
        if (child.getDuplicateGroup() != null) throw new InvalidStateException("Complaint " + child.getTrackingId() + " is already linked");
        if (child.getStatus().isFinal() || master.getStatus().isFinal()) {
            throw new BusinessRuleException("COMPLAINT_CLOSED", "Closed or rejected complaints cannot be linked");
        }
        if (master.isLinkedDuplicate()) {
            master = complaints.findWithDetails(master.getDuplicateGroup().getMasterComplaintId()).orElseThrow();
        }
        DuplicateGroup group = master.getDuplicateGroup();
        if (group == null) {
            group = groups.save(new DuplicateGroup(master.getId(), officer.id()));
            master.linkTo(group);
            complaints.saveAndFlush(master);
        }
        User me = users.getReferenceById(officer.id());
        child.linkTo(group);
        workflow.note(child, "Linked to existing complaint " + master.getTrackingId()
                + " about the same issue. Both will be resolved together.", true, me);
        complaints.saveAndFlush(child);

        if (master.getClassificationSource() == ClassificationSource.OFFICER_CONFIRMED
                || master.getClassificationSource() == ClassificationSource.OFFICER_CORRECTED) {
            if (master.getStatus() == ComplaintStatus.RESOLVED) {
                if (child.getStatus() == ComplaintStatus.UNDER_REVIEW) {
                    child.approveClassification(master.getCategory(), master.getDepartment(), master.getPriority(), me, false,
                            master.getSlaDueAt());
                }
                if (child.getStatus().isSlaActive() || child.getStatus() == ComplaintStatus.UNDER_REVIEW) {
                    ComplaintStatus from = child.resolveWithMaster();
                    workflow.note(child, "Resolved together with " + master.getTrackingId() + " (was " + from + ")", true, me);
                    complaints.saveAndFlush(child);
                }
            } else if (master.getStatus().isSlaActive()) {
                workflow.acceptWithMaster(child, master, me);
            }
        }
        dc.decide(CandidateStatus.APPROVED, officer.id());
        for (DuplicateCandidate other : candidates.findForComplaint(child.getId())) {        // the others are now moot
            if (other.getStatus() == CandidateStatus.PENDING && !other.getId().equals(dc.getId())) {
                other.decide(CandidateStatus.REJECTED, officer.id());
            }
        }
        notifications.notify(child.getCitizenId(), child.getId(), "LINKED", "Your complaint " + child.getTrackingId()
                + " is about the same issue as " + master.getTrackingId() + ". It will be resolved together; you keep your tracking ID.");
        audit.log(officer.id(), "DUPLICATE_LINKED", "COMPLAINT", child.getId(), "master=" + master.getId() + " group=" + group.getId());
        return DuplicateCandidateResponse.from(dc);
    }

    @Transactional
    public DuplicateCandidateResponse reject(Long candidateId, AppUserPrincipal officer) {
        DuplicateCandidate dc = candidates.findWithComplaints(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Duplicate suggestion", candidateId));
        access.assertDepartmentOfficer(officer, dc.getCandidate());
        dc.decide(CandidateStatus.REJECTED, officer.id());
        audit.log(officer.id(), "DUPLICATE_REJECTED", "DUPLICATE_CANDIDATE", dc.getId(), null);
        return DuplicateCandidateResponse.from(dc);
    }
}
