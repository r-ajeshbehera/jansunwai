package com.jansunwai.dto.response;

import com.jansunwai.repository.DashboardRepository;
import com.jansunwai.repository.GeoRepository;

import java.util.List;
import java.util.Map;

public record DashboardSummaryResponse(String scope, String from, String to, DashboardRepository.Totals totals,
                                       List<DashboardRepository.Count> byCategory, List<DashboardRepository.Count> byStatus,
                                       List<DashboardRepository.Count> byWard,
                                       List<DashboardRepository.DepartmentPerformance> departments,
                                       List<Map<String, Object>> trend, List<GeoRepository.Hotspot> hotspots) { }
