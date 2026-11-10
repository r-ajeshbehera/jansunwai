package com.jansunwai.controller;

import com.jansunwai.dto.response.PublicTrackingResponse;
import com.jansunwai.service.ComplaintService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** No login. Rate-limited per IP by RateLimitFilter. */
@RestController
@RequestMapping("/api/public")
@Validated
@Tag(name = "Public")
public class PublicController {
    private final ComplaintService complaints;

    public PublicController(ComplaintService complaints) {
        this.complaints = complaints;
    }

    @GetMapping("/track/{trackingId}")
    public PublicTrackingResponse track(@PathVariable @Pattern(regexp = "(?i)JS-\\d{4}-\\d{6}", message = "invalid tracking ID")
                                        String trackingId) {
        return complaints.track(trackingId);
    }
}
