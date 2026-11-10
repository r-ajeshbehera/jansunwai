package com.jansunwai.controller;

import com.jansunwai.dto.request.*;
import com.jansunwai.dto.response.*;
import com.jansunwai.entity.ComplaintStatus;
import com.jansunwai.security.CurrentUser;
import com.jansunwai.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/complaints")
@Tag(name = "Complaints")
public class ComplaintController {
    private final ComplaintService complaints;
    private final WorkflowService workflow;
    private final AssignmentService assignments;
    private final AttachmentService attachments;
    private final RemarkService remarks;

    public ComplaintController(ComplaintService complaints, WorkflowService workflow, AssignmentService assignments,
                               AttachmentService attachments, RemarkService remarks) {
        this.complaints = complaints;
        this.workflow = workflow;
        this.assignments = assignments;
        this.attachments = attachments;
        this.remarks = remarks;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "Submit a complaint (en/hi/or). AI analysis runs in the background after saving.")
    public ComplaintDetail submit(@Valid @RequestBody CreateComplaintRequest req) {
        return complaints.submit(req, CurrentUser.get());
    }

    @GetMapping
    @Operation(summary = "List complaints visible to the caller (citizen: own, field officer: assigned, officer: department)")
    public PageResponse<ComplaintSummary> list(@RequestParam(required = false) ComplaintStatus status,
                                               @RequestParam(required = false) Long departmentId,
                                               @RequestParam(required = false) Long wardId,
                                               @RequestParam(defaultValue = "false") boolean overdueOnly,
                                               @RequestParam(required = false) String q,
                                               @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return complaints.list(CurrentUser.get(), status, departmentId, wardId, overdueOnly, q, Pages.capped(pageable));
    }

    @GetMapping("/{id}")
    public ComplaintDetail get(@PathVariable Long id) {
        return complaints.get(id, CurrentUser.get());
    }

    @GetMapping("/track/{trackingId}")
    @Operation(summary = "Public tracking by tracking ID (no login, no personal data)")
    public PublicTrackingResponse track(@PathVariable String trackingId) {
        return complaints.track(trackingId);
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('DEPARTMENT_OFFICER','ADMIN')")
    @Operation(summary = "Approve (with human-confirmed classification), reroute or reject an UNDER_REVIEW complaint")
    public ComplaintDetail review(@PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
        return workflow.review(id, req, CurrentUser.get());
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Workflow step: start work, submit resolution, approve resolution, send back, close, reopen")
    public ComplaintDetail changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest req) {
        return workflow.changeStatus(id, req, CurrentUser.get());
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('DEPARTMENT_OFFICER','ADMIN')")
    public ComplaintDetail assign(@PathVariable Long id, @Valid @RequestBody AssignRequest req) {
        return assignments.assign(id, req, CurrentUser.get());
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CITIZEN','FIELD_OFFICER')")
    @Operation(summary = "Upload a JPEG/PNG/WEBP photo (citizen: evidence, assigned field officer: resolution proof)")
    public AttachmentResponse upload(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return attachments.upload(id, file, CurrentUser.get());
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id, @PathVariable Long attachmentId) throws IOException {
        AttachmentService.FileDownload f = attachments.download(id, attachmentId, CurrentUser.get());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .contentLength(f.size())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(f.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(f.stream()));
    }

    @PostMapping("/{id}/remarks")
    @ResponseStatus(HttpStatus.CREATED)
    public RemarkResponse addRemark(@PathVariable Long id, @Valid @RequestBody RemarkRequest req) {
        return remarks.add(id, req, CurrentUser.get());
    }
}
