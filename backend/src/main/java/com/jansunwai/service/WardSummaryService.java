package com.jansunwai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jansunwai.ai.AiGateway;
import com.jansunwai.ai.AiProviderException;
import com.jansunwai.ai.MockAiProvider;
import com.jansunwai.ai.WardFacts;
import com.jansunwai.config.AppProperties;
import com.jansunwai.dto.response.WardSummaryResponse;
import com.jansunwai.entity.Ward;
import com.jansunwai.entity.WardSummary;
import com.jansunwai.exception.ResourceNotFoundException;
import com.jansunwai.repository.DashboardRepository;
import com.jansunwai.repository.GeoRepository;
import com.jansunwai.repository.WardRepository;
import com.jansunwai.repository.WardSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Weekly ward intelligence: FACTS come from SQL (traceable), the NARRATIVE comes from the AI and is labelled as an
 * interpretation. If the AI fails, a deterministic template is used instead (provider = "template-fallback").
 */
@Service
public class WardSummaryService {
    private static final Logger log = LoggerFactory.getLogger(WardSummaryService.class);

    private final WardRepository wards;
    private final WardSummaryRepository summaries;
    private final DashboardRepository dashboard;
    private final GeoRepository geo;
    private final AiGateway gateway;
    private final ObjectMapper json;
    private final MockAiProvider fallback;

    public WardSummaryService(WardRepository wards, WardSummaryRepository summaries, DashboardRepository dashboard,
                              GeoRepository geo, AiGateway gateway, ObjectMapper json, AppProperties props) {
        this.wards = wards;
        this.summaries = summaries;
        this.dashboard = dashboard;
        this.geo = geo;
        this.gateway = gateway;
        this.json = json;
        this.fallback = new MockAiProvider(props);
    }

    public static LocalDate weekStart(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    @Transactional
    public WardSummaryResponse generate(Long wardId, LocalDate anyDayInWeek) {
        Ward ward = wards.findById(wardId).orElseThrow(() -> new ResourceNotFoundException("Ward", wardId));
        LocalDate week = weekStart(anyDayInWeek);
        WardFacts facts = facts(ward, week);
        String narrative;
        String provider;
        if (facts.received() == 0 && facts.open() == 0) {
            narrative = ward.getName() + " had no complaints in this week and has no open complaints.";
            provider = "no-data";
        } else {
            try {
                narrative = gateway.call("narrate", () -> gateway.provider().narrate(facts)).value().text();
                provider = gateway.provider().name();
            } catch (AiProviderException e) {
                log.warn("Ward summary AI failed for ward {}: {}", wardId, e.getCode());
                narrative = fallback.narrate(facts).text().replace("(Mock AI narrative) ", "(Automatic template) ");
                provider = "template-fallback";
            }
        }
        String factsJson = toJson(facts);
        WardSummary s = summaries.findByWardIdAndWeekStart(wardId, week).orElse(null);
        if (s == null) s = summaries.save(new WardSummary(wardId, week, factsJson, narrative, provider));
        else s.replace(factsJson, narrative, provider);
        return toResponse(s, facts);
    }

    @Transactional
    public List<WardSummaryResponse> generateAll(LocalDate day) {
        return wards.findAllByOrderByCode().stream().map(w -> generate(w.getId(), day)).toList();
    }

    @Transactional(readOnly = true)
    public List<WardSummaryResponse> forWeek(LocalDate day) {
        return summaries.findByWeekStartOrderByWardId(weekStart(day)).stream().map(s -> toResponse(s, fromJson(s.getFactsJson()))).toList();
    }

    WardFacts facts(Ward ward, LocalDate week) {
        Instant from = week.atStartOfDay(DashboardService.IST).toInstant();
        Instant to = week.plusDays(7).atStartOfDay(DashboardService.IST).toInstant();
        var weekTotals = dashboard.totals(null, ward.getId(), from, to);
        var allTime = dashboard.totals(null, ward.getId(), Instant.EPOCH, Instant.now().plusSeconds(60));
        Map<String, Long> byCategory = new LinkedHashMap<>();
        dashboard.byCategory(null, ward.getId(), from, to).forEach(c -> byCategory.put(c.key(), c.count()));
        String top = byCategory.isEmpty() ? null : byCategory.keySet().iterator().next();
        long hotspots = geo.hotspots(250, 2).stream().filter(h -> ward.getName().equals(h.wardName())).count();
        return new WardFacts(ward.getName(), week.toString(), weekTotals.total(), weekTotals.resolved(), allTime.pending(),
                allTime.overdue(), byCategory, top, hotspots);
    }

    private WardSummaryResponse toResponse(WardSummary s, WardFacts facts) {
        boolean ai = !s.getProvider().equals("template-fallback") && !s.getProvider().equals("no-data");
        return new WardSummaryResponse(s.getWardId(), s.getWeekStart().toString(), facts, s.getNarrative(), s.getProvider(),
                ai, s.getCreatedAt());
    }

    private String toJson(WardFacts f) {
        try {
            return json.writeValueAsString(f);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private WardFacts fromJson(String s) {
        try {
            return json.readValue(s, WardFacts.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
