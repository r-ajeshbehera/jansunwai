package com.jansunwai.controller;

import com.jansunwai.ai.AiGateway;
import com.jansunwai.dto.response.AiAnalysisResponse;
import com.jansunwai.dto.response.AiUsageResponse;
import com.jansunwai.dto.response.DuplicateCandidateResponse;
import com.jansunwai.entity.Complaint;
import com.jansunwai.exception.ResourceNotFoundException;
import com.jansunwai.repository.ComplaintAiAnalysisRepository;
import com.jansunwai.repository.ComplaintRepository;
import com.jansunwai.security.AccessPolicy;
import com.jansunwai.security.CurrentUser;
import com.jansunwai.service.AiPipelineService;
import com.jansunwai.service.AiUsageService;
import com.jansunwai.service.DuplicateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI: classification, duplicates, usage")
@PreAuthorize("hasAnyRole('DEPARTMENT_OFFICER','ADMIN')")
public class AiController {
    private final AiPipelineService pipeline;
    private final DuplicateService duplicates;
    private final AiUsageService usage;
    private final ComplaintRepository complaints;
    private final ComplaintAiAnalysisRepository analyses;
    private final AccessPolicy access;
    private final AiGateway gateway;

    public AiController(AiPipelineService pipeline, DuplicateService duplicates, AiUsageService usage,
                        ComplaintRepository complaints, ComplaintAiAnalysisRepository analyses, AccessPolicy access,
                        AiGateway gateway) {
        this.pipeline = pipeline;
        this.duplicates = duplicates;
        this.usage = usage;
        this.complaints = complaints;
        this.analyses = analyses;
        this.access = access;
        this.gateway = gateway;
    }

    @PostMapping("/classify/{complaintId}")
    @Operation(summary = "Re-run AI classification (suggestion only; the officer still decides)")
    public AiAnalysisResponse classify(@PathVariable Long complaintId) {
        return pipeline.reclassify(complaintId, CurrentUser.get());
    }

    @GetMapping("/analysis/{complaintId}")
    @Transactional(readOnly = true)
    public List<AiAnalysisResponse> analysis(@PathVariable Long complaintId) {
        Complaint c = complaints.findWithDetails(complaintId).orElseThrow(() -> new ResourceNotFoundException("Complaint", complaintId));
        access.assertCanView(CurrentUser.get(), c);
        return analyses.findByComplaintIdOrderByCreatedAtDesc(complaintId).stream().map(AiAnalysisResponse::from).toList();
    }

    @PostMapping("/duplicates/{complaintId}/detect")
    public List<DuplicateCandidateResponse> detect(@PathVariable Long complaintId) {
        return duplicates.detectFor(complaintId, CurrentUser.get());
    }

    @GetMapping("/duplicates/pending")
    public List<DuplicateCandidateResponse> pending() {
        return duplicates.pending(CurrentUser.get());
    }

    @PostMapping("/duplicates/candidates/{candidateId}/approve")
    @Operation(summary = "Human approval: link the newer complaint to the older one (each keeps its tracking ID)")
    public DuplicateCandidateResponse approve(@PathVariable Long candidateId) {
        return duplicates.approve(candidateId, CurrentUser.get());
    }

    @PostMapping("/duplicates/candidates/{candidateId}/reject")
    public DuplicateCandidateResponse reject(@PathVariable Long candidateId) {
        return duplicates.reject(candidateId, CurrentUser.get());
    }

    @GetMapping("/usage")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "AI calls, failures, tokens, estimated cost and latency per provider/model")
    public List<AiUsageResponse> usage() {
        return usage.usage();
    }

    @GetMapping("/provider")
    public Map<String, Object> provider() {
        var p = gateway.provider();
        return Map.of("provider", p.name(), "chatModel", p.chatModel(), "embeddingModel", p.embeddingModel(),
                "mock", "mock".equals(p.name()));
    }
}
