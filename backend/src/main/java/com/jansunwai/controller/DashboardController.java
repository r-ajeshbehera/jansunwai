package com.jansunwai.controller;

import com.jansunwai.dto.response.DashboardSummaryResponse;
import com.jansunwai.dto.response.WardSummaryResponse;
import com.jansunwai.repository.GeoRepository;
import com.jansunwai.security.CurrentUser;
import com.jansunwai.service.DashboardService;
import com.jansunwai.service.GeoService;
import com.jansunwai.service.WardSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Dashboards, maps & ward intelligence")
public class DashboardController {
    private final DashboardService dashboards;
    private final WardSummaryService summaries;
    private final GeoService geo;

    public DashboardController(DashboardService dashboards, WardSummaryService summaries, GeoService geo) {
        this.dashboards = dashboards;
        this.summaries = summaries;
        this.geo = geo;
    }

    @GetMapping("/dashboard/summary")
    @PreAuthorize("hasAnyRole('DEPARTMENT_OFFICER','ADMIN')")
    public DashboardSummaryResponse summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboards.summary(CurrentUser.get(), from, to);
    }

    @GetMapping("/dashboard/ward/{wardId}")
    @PreAuthorize("hasAnyRole('DEPARTMENT_OFFICER','ADMIN')")
    public DashboardSummaryResponse ward(@PathVariable Long wardId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboards.ward(wardId, CurrentUser.get(), from, to);
    }

    @GetMapping("/dashboard/department/{departmentId}")
    @PreAuthorize("hasAnyRole('DEPARTMENT_OFFICER','ADMIN')")
    public DashboardSummaryResponse department(@PathVariable Long departmentId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboards.department(departmentId, CurrentUser.get(), from, to);
    }

    @GetMapping("/dashboard/weekly-summary")
    @PreAuthorize("hasAnyRole('DEPARTMENT_OFFICER','ADMIN')")
    @Operation(summary = "Ward summaries for the week containing 'date' (facts from SQL + labelled AI narrative)")
    public List<WardSummaryResponse> weekly(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return summaries.forWeek(date == null ? LocalDate.now(ZoneId.of("Asia/Kolkata")) : date);
    }

    @PostMapping("/dashboard/weekly-summary/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public List<WardSummaryResponse> generate(@RequestParam(required = false) Long wardId,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate d = date == null ? LocalDate.now(ZoneId.of("Asia/Kolkata")) : date;
        return wardId == null ? summaries.generateAll(d) : List.of(summaries.generate(wardId, d));
    }

    @GetMapping("/geo/nearby-summary")
    @Operation(summary = "Counts of open complaints near a point (shown before submitting, to reduce duplicates)")
    public List<GeoRepository.NearbyCount> nearby(@RequestParam double lat, @RequestParam double lng,
                                                  @RequestParam(defaultValue = "250") int radiusMeters) {
        return geo.nearbySummary(lat, lng, radiusMeters);
    }

    @GetMapping("/geo/heatmap")
    @PreAuthorize("hasAnyRole('DEPARTMENT_OFFICER','ADMIN')")
    public List<GeoRepository.HeatPoint> heatmap(@RequestParam(required = false) Long wardId,
                                                 @RequestParam(required = false) Long departmentId) {
        return geo.heatmap(wardId, departmentId, CurrentUser.get());
    }

    @GetMapping("/geo/hotspots")
    @PreAuthorize("hasAnyRole('DEPARTMENT_OFFICER','ADMIN')")
    public List<GeoRepository.Hotspot> hotspots() {
        return geo.hotspots();
    }
}
