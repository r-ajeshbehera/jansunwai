package com.jansunwai.service;

import com.jansunwai.ai.*;
import com.jansunwai.config.AppProperties;
import com.jansunwai.dto.response.AiAnalysisResponse;
import com.jansunwai.entity.*;
import com.jansunwai.exception.InvalidStateException;
import com.jansunwai.exception.ResourceNotFoundException;
import com.jansunwai.notification.NotificationService;
import com.jansunwai.repository.*;
import com.jansunwai.security.AccessPolicy;
import com.jansunwai.security.AppUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * After a complaint is saved: classify -> store analysis -> suggest routing -> UNDER_REVIEW -> embed -> find duplicates.
 *
 * The slow AI call happens OUTSIDE any database transaction (short transactions before and after), so a slow
 * provider never holds a DB connection or row lock. If the AI fails, the complaint is NOT lost: it waits for the
 * retry job and, after the maximum attempts, goes to manual review. AI never rejects or approves anything.
 */
@Service
public class AiPipelineService {
    private static final Logger log = LoggerFactory.getLogger(AiPipelineService.class);

    private final ComplaintRepository complaints;
    private final ComplaintAiAnalysisRepository analyses;
    private final DepartmentRepository departments;
    private final EmbeddingRepository embeddings;
    private final AiGateway gateway;
    private final AiOutputValidator validator;
    private final WorkflowService workflow;
    private final DuplicateService duplicates;
    private final NotificationService notifications;
    private final AccessPolicy access;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final AppProperties.Ai cfg;

    public AiPipelineService(ComplaintRepository complaints, ComplaintAiAnalysisRepository analyses,
                             DepartmentRepository departments, EmbeddingRepository embeddings, AiGateway gateway,
                             AiOutputValidator validator, WorkflowService workflow, DuplicateService duplicates,
                             NotificationService notifications, AccessPolicy access, AuditService audit,
                             PlatformTransactionManager txManager, AppProperties props) {
        this.complaints = complaints;
        this.analyses = analyses;
        this.departments = departments;
        this.embeddings = embeddings;
        this.gateway = gateway;
        this.validator = validator;
        this.workflow = workflow;
        this.duplicates = duplicates;
        this.notifications = notifications;
        this.access = access;
        this.audit = audit;
        this.tx = new TransactionTemplate(txManager);
        this.cfg = props.ai();
    }

    @Async("aiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmitted(ComplaintSubmittedEvent event) {
        process(event.complaintId());
    }

    /** Safe to call more than once (retry job): it only acts on complaints still waiting for AI. */
    public void process(Long complaintId) {
        AiProvider.ComplaintText text = tx.execute(s -> {
            Complaint c = complaints.findById(complaintId).orElse(null);
            if (c == null) return null;
            if (c.getStatus() == ComplaintStatus.SUBMITTED) {
                workflow.record(c, ComplaintStatus.AI_ANALYSIS_PENDING, "Automatic analysis started", false, null);
            }
            if (c.getStatus() != ComplaintStatus.AI_ANALYSIS_PENDING) return null;
            c.incrementAiAttempts();
            complaints.saveAndFlush(c);
            return new AiProvider.ComplaintText(c.getTitle(), c.getDescription(), c.getLanguage());
        });
        if (text == null) return;

        AiClassification result = null;
        String error = null;
        int latency = 0;
        Integer inTokens = null, outTokens = null;
        try {
            AiGateway.Timed<AiProvider.ClassificationResult> r = gateway.call("classify", () -> gateway.provider().classify(text));
            latency = r.latencyMs();
            inTokens = r.value().inputTokens();
            outTokens = r.value().outputTokens();
            result = validator.validate(r.value().raw(), text.languageHint(), departmentCodes());
        } catch (AiProviderException e) {
            error = e.getCode().name();
            log.warn("AI classification failed for complaint {}: {}", complaintId, e.getCode());
        }

        final AiClassification ok = result;
        final String err = error;
        final int ms = latency;
        final Integer in = inTokens, out = outTokens;
        boolean classified = Boolean.TRUE.equals(tx.execute(s -> saveOutcome(complaintId, ok, err, ms, in, out)));
        if (!classified) return;

        // ok is null when classification failed for good and the complaint went to manual review: still embed it,
        // so duplicate detection works for it as well (embedding may be a different, working service).
        String embedText = ok != null && ok.translationEn() != null ? text.title() + ". " + ok.translationEn()
                : text.title() + ". " + text.description();
        if (embedAndStore(complaintId, embedText)) {
            try {
                duplicates.detect(complaintId);
            } catch (RuntimeException e) {
                log.warn("Duplicate detection failed for complaint {}: {}", complaintId, e.getMessage());
            }
        }
    }

    /** @return true when the complaint moved to UNDER_REVIEW (classified or sent to manual review). */
    private boolean saveOutcome(Long id, AiClassification r, String error, int latencyMs, Integer in, Integer out) {
        Complaint c = complaints.findWithDetails(id).orElseThrow();
        if (c.getStatus() != ComplaintStatus.AI_ANALYSIS_PENDING) return false;
        AiProvider p = gateway.provider();
        if (r == null) {
            analyses.save(ComplaintAiAnalysis.failed(id, p.name(), p.chatModel(), error, latencyMs));
            if (c.getAiAttempts() < cfg.maxAttempts()) return false;          // the retry job will try again
            workflow.record(c, ComplaintStatus.UNDER_REVIEW, "Under review. Automatic classification was unavailable ("
                    + error + "), so an officer will classify it.", true, null);
            complaints.saveAndFlush(c);
            notifications.notifyAdmins(id, "MANUAL_REVIEW", "Complaint " + c.getTrackingId() + " needs manual classification.");
            return true;
        }
        boolean confident = r.confidence().doubleValue() >= cfg.minConfidence();
        analyses.save(ComplaintAiAnalysis.succeeded(id, p.name(), p.chatModel(), r.category(), r.departmentCode(), r.priority(),
                r.language(), r.summaryEn(), r.translationEn(), r.suggestedActions(), r.confidence(), true /* a human always reviews */,
                latencyMs, in, out, cost(in, out)));
        Department dept = confident ? departments.findByCode(r.departmentCode()).orElse(null) : null;
        c.suggestClassification(r.category(), dept, r.priority());
        workflow.record(c, ComplaintStatus.UNDER_REVIEW, "Under review by the municipality", true, null);
        complaints.saveAndFlush(c);
        String msg = "New complaint " + c.getTrackingId() + " (AI suggests " + r.category() + ", " + r.priority()
                + ", confidence " + r.confidence() + (confident ? ")" : ", LOW: please classify manually)");
        notifications.notifyDepartmentOfficers(dept == null ? null : dept.getId(), id, "NEW_COMPLAINT", msg);
        return true;
    }

    private boolean embedAndStore(Long complaintId, String text) {
        try {
            AiGateway.Timed<float[]> v = gateway.call("embed", () -> gateway.provider().embed(text));
            embeddings.upsert(complaintId, v.value(), gateway.provider().embeddingModel());
            return true;
        } catch (AiProviderException e) {
            log.warn("Embedding failed for complaint {}: {} (the backfill job will retry)", complaintId, e.getCode());
            return false;
        }
    }

    /** Embeds complaints that have no vector yet (after a provider change or an earlier failure). */
    public int backfillEmbeddings(int limit) {
        int n = 0;
        for (Long id : embeddings.complaintsWithoutEmbedding(limit)) {
            Complaint c = complaints.findById(id).orElse(null);
            if (c != null && embedAndStore(id, c.getTitle() + ". " + c.getDescription())) n++;
        }
        return n;
    }

    /** Officer-triggered re-run (e.g. after the provider was down). Updates the suggestion, never the decision. */
    public AiAnalysisResponse reclassify(Long complaintId, AppUserPrincipal officer) {
        AiProvider.ComplaintText text = tx.execute(s -> {
            Complaint c = complaints.findWithDetails(complaintId).orElseThrow(() -> new ResourceNotFoundException("Complaint", complaintId));
            access.assertDepartmentOfficer(officer, c);
            if (c.getStatus() != ComplaintStatus.UNDER_REVIEW) {
                throw new InvalidStateException("AI can only be re-run while the complaint is UNDER_REVIEW");
            }
            return new AiProvider.ComplaintText(c.getTitle(), c.getDescription(), c.getLanguage());
        });
        AiProvider p = gateway.provider();
        try {
            AiGateway.Timed<AiProvider.ClassificationResult> r = gateway.call("classify", () -> p.classify(text));
            AiClassification v = validator.validate(r.value().raw(), text.languageHint(), departmentCodes());
            return tx.execute(s -> {
                Complaint c = complaints.findWithDetails(complaintId).orElseThrow();
                ComplaintAiAnalysis a = analyses.save(ComplaintAiAnalysis.succeeded(complaintId, p.name(), p.chatModel(),
                        v.category(), v.departmentCode(), v.priority(), v.language(), v.summaryEn(), v.translationEn(),
                        v.suggestedActions(), v.confidence(), true, r.latencyMs(), r.value().inputTokens(),
                        r.value().outputTokens(), cost(r.value().inputTokens(), r.value().outputTokens())));
                if (c.getClassificationSource() == ClassificationSource.NONE
                        || c.getClassificationSource() == ClassificationSource.AI_SUGGESTED) {
                    Department dept = v.confidence().doubleValue() >= cfg.minConfidence()
                            ? departments.findByCode(v.departmentCode()).orElse(c.getDepartment()) : c.getDepartment();
                    c.suggestClassification(v.category(), dept, v.priority());
                    complaints.saveAndFlush(c);
                }
                audit.log(officer.id(), "AI_RECLASSIFIED", "COMPLAINT", complaintId, v.category().name());
                return AiAnalysisResponse.from(a);
            });
        } catch (AiProviderException e) {
            return tx.execute(s -> {
                ComplaintAiAnalysis a = analyses.save(ComplaintAiAnalysis.failed(complaintId, p.name(), p.chatModel(),
                        e.getCode().name(), 0));
                return AiAnalysisResponse.from(a);
            });
        }
    }

    private Set<String> departmentCodes() {
        return departments.findAll().stream().filter(Department::isActive).map(Department::getCode).collect(Collectors.toSet());
    }

    private BigDecimal cost(Integer in, Integer out) {
        BigDecimal c = BigDecimal.ZERO;
        if (in != null && cfg.inputCostPer1k() != null) c = c.add(cfg.inputCostPer1k().multiply(BigDecimal.valueOf(in)).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP));
        if (out != null && cfg.outputCostPer1k() != null) c = c.add(cfg.outputCostPer1k().multiply(BigDecimal.valueOf(out)).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP));
        return c;
    }
}
