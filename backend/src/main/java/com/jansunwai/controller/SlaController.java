package com.jansunwai.controller;

import com.jansunwai.dto.request.SlaConfigRequest;
import com.jansunwai.dto.response.ComplaintSummary;
import com.jansunwai.dto.response.EscalationResponse;
import com.jansunwai.dto.response.PageResponse;
import com.jansunwai.dto.response.SlaConfigResponse;
import com.jansunwai.security.CurrentUser;
import com.jansunwai.service.ComplaintService;
import com.jansunwai.service.EscalationService;
import com.jansunwai.service.SlaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "SLA & escalations")
@PreAuthorize("hasAnyRole('DEPARTMENT_OFFICER','ADMIN')")
public class SlaController {
    private final SlaService sla;
    private final EscalationService escalations;
    private final ComplaintService complaints;

    public SlaController(SlaService sla, EscalationService escalations, ComplaintService complaints) {
        this.sla = sla;
        this.escalations = escalations;
        this.complaints = complaints;
    }

    @GetMapping("/sla/configurations")
    public List<SlaConfigResponse> configurations() {
        return sla.list();
    }

    @PostMapping("/sla/configurations")
    @PreAuthorize("hasRole('ADMIN')")
    public SlaConfigResponse save(@Valid @RequestBody SlaConfigRequest req) {
        return sla.save(req, CurrentUser.get());
    }

    @GetMapping("/sla/overdue")
    public PageResponse<ComplaintSummary> overdue(@PageableDefault(size = 20, sort = "slaDueAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return complaints.list(CurrentUser.get(), null, null, null, true, null, Pages.capped(pageable));
    }

    @GetMapping("/escalations")
    public PageResponse<EscalationResponse> escalations(@PageableDefault(size = 20) Pageable pageable) {
        return escalations.list(CurrentUser.get(), Pages.capped(pageable));
    }
}
