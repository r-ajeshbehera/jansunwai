package com.jansunwai.service;

import com.jansunwai.config.AppProperties;
import com.jansunwai.dto.response.EscalationResponse;
import com.jansunwai.dto.response.PageResponse;
import com.jansunwai.entity.Complaint;
import com.jansunwai.entity.ComplaintStatus;
import com.jansunwai.entity.EscalationHistory;
import com.jansunwai.notification.NotificationService;
import com.jansunwai.repository.ComplaintRepository;
import com.jansunwai.repository.ComplaintSqlRepository;
import com.jansunwai.repository.EscalationHistoryRepository;
import com.jansunwai.security.AppUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SLA monitoring, used by SlaMonitorJob. Idempotent by design (running it twice, or on two servers, is harmless):
 *  - warning:   conditional UPDATE "... WHERE sla_warned_at IS NULL" -> only one run sends the notification,
 *  - escalation: INSERT ... ON CONFLICT (complaint_id, level) DO NOTHING -> only one row per level, ever.
 * Each complaint is processed in its own short transaction so one failure does not undo the others.
 */
@Service
public class EscalationService {
    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);
    static final EnumSet<ComplaintStatus> SLA = ComplaintService.SLA_STATUSES;

    private final ComplaintRepository complaints;
    private final ComplaintSqlRepository sql;
    private final EscalationHistoryRepository escalations;
    private final SlaService sla;
    private final WorkflowService workflow;
    private final NotificationService notifications;
    private final TransactionTemplate tx;
    private final int level2AfterHours;
    private final int autoCloseDays;

    public EscalationService(ComplaintRepository complaints, ComplaintSqlRepository sql, EscalationHistoryRepository escalations,
                             SlaService sla, WorkflowService workflow, NotificationService notifications,
                             PlatformTransactionManager txManager, AppProperties props) {
        this.complaints = complaints;
        this.sql = sql;
        this.escalations = escalations;
        this.sla = sla;
        this.workflow = workflow;
        this.notifications = notifications;
        this.tx = new TransactionTemplate(txManager);
        this.level2AfterHours = props.sla().level2AfterHours();
        this.autoCloseDays = props.sla().autoCloseDays();
    }

    public record RunResult(int warnings, int level1, int level2, int autoClosed) { }

    public RunResult runOnce(Instant now) {
        int warnings = 0, l1 = 0, l2 = 0, closed = 0;
        for (Complaint c : complaints.findSlaWarningCandidates(SLA)) {
            if (!sla.isWarningDue(c, now)) continue;
            if (Boolean.TRUE.equals(tx.execute(s -> warn(c)))) warnings++;
        }
        for (Complaint c : complaints.findOverdue(SLA, now, 1)) {
            if (Boolean.TRUE.equals(tx.execute(s -> escalate(c, 1, "Resolution deadline passed")))) l1++;
        }
        Instant level2Before = now.minus(Duration.ofHours(level2AfterHours));
        for (Complaint c : complaints.findOverdue(SLA, level2Before, 2)) {
            if (Boolean.TRUE.equals(tx.execute(s -> escalate(c, 2, "Still unresolved " + level2AfterHours + " h after the deadline")))) l2++;
        }
        for (Complaint c : complaints.findResolvedBefore(now.minus(Duration.ofDays(autoCloseDays)))) {
            try {
                tx.executeWithoutResult(s -> autoClose(c.getId()));
                closed++;
            } catch (RuntimeException e) {
                log.warn("Auto-close skipped for complaint {}: {}", c.getId(), e.getMessage());
            }
        }
        if (warnings + l1 + l2 + closed > 0) {
            log.info("SLA run: {} warnings, {} level-1, {} level-2 escalations, {} auto-closed", warnings, l1, l2, closed);
        }
        return new RunResult(warnings, l1, l2, closed);
    }

    private boolean warn(Complaint c) {
        if (!sql.markSlaWarned(c.getId())) return false;              // someone else already warned
        notifications.notifyDepartmentOfficers(c.getDepartmentId(), c.getId(), "SLA_WARNING",
                "Complaint " + c.getTrackingId() + " is close to its deadline (" + WorkflowService.DUE_FORMAT.format(c.getSlaDueAt()) + ").");
        return true;
    }

    private boolean escalate(Complaint c, int level, String reason) {
        if (escalations.insertIfAbsent(c.getId(), level, reason, c.getSlaDueAt()) == 0) return false;   // already escalated
        sql.raiseEscalationLevel(c.getId(), level);
        Complaint fresh = complaints.findById(c.getId()).orElseThrow();
        workflow.note(fresh, "Escalated (level " + level + "): " + reason, true, null);
        String msg = "ESCALATION L" + level + ": complaint " + c.getTrackingId() + " - " + reason + ".";
        if (level == 1) notifications.notifyDepartmentOfficers(c.getDepartmentId(), c.getId(), "ESCALATION", msg);
        else notifications.notifyAdmins(c.getId(), "ESCALATION", msg);
        return true;
    }

    private void autoClose(Long id) {
        Complaint c = complaints.findById(id).orElseThrow();
        if (c.getStatus() != ComplaintStatus.RESOLVED) return;
        workflow.record(c, ComplaintStatus.CLOSED, "Closed automatically " + autoCloseDays + " days after resolution", true, null);
        complaints.saveAndFlush(c);
    }

    @Transactional(readOnly = true)
    public PageResponse<EscalationResponse> list(AppUserPrincipal user, Pageable pageable) {
        var page = user.isAdmin() ? escalations.findAllByOrderByCreatedAtDesc(pageable)
                : escalations.findForDepartment(user.departmentId(), pageable);
        Map<Long, String> tracking = complaints.findAllById(page.getContent().stream().map(EscalationHistory::getComplaintId).toList())
                .stream().collect(Collectors.toMap(Complaint::getId, Complaint::getTrackingId));
        return PageResponse.of(page, e -> new EscalationResponse(e.getId(), e.getComplaintId(), tracking.get(e.getComplaintId()),
                e.getLevel(), e.getReason(), e.getDueAt(), e.getCreatedAt()));
    }
}
