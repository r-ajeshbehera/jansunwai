package com.jansunwai.service;

import com.jansunwai.dto.response.DashboardSummaryResponse;
import com.jansunwai.exception.ResourceNotFoundException;
import com.jansunwai.repository.DashboardRepository;
import com.jansunwai.repository.DepartmentRepository;
import com.jansunwai.repository.GeoRepository;
import com.jansunwai.repository.WardRepository;
import com.jansunwai.security.AppUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Dashboard numbers come only from SQL aggregates. A department officer always sees only their own department. */
@Service
public class DashboardService {
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final DashboardRepository repo;
    private final GeoRepository geo;
    private final DepartmentRepository departments;
    private final WardRepository wards;

    public DashboardService(DashboardRepository repo, GeoRepository geo, DepartmentRepository departments, WardRepository wards) {
        this.repo = repo;
        this.geo = geo;
        this.departments = departments;
        this.wards = wards;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(AppUserPrincipal user, LocalDate from, LocalDate to) {
        Long dept = user.isAdmin() ? null : user.departmentId();
        return build(user.isAdmin() ? "All departments" : departments.findById(dept).map(d -> d.getName()).orElse("Department"),
                dept, null, from, to, user.isAdmin());
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse department(Long departmentId, AppUserPrincipal user, LocalDate from, LocalDate to) {
        if (!user.isAdmin() && !departmentId.equals(user.departmentId())) throw new ResourceNotFoundException("Department", departmentId);
        String name = departments.findById(departmentId).map(d -> d.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));
        return build(name, departmentId, null, from, to, false);
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse ward(Long wardId, AppUserPrincipal user, LocalDate from, LocalDate to) {
        String name = wards.findById(wardId).map(w -> w.getName()).orElseThrow(() -> new ResourceNotFoundException("Ward", wardId));
        Long dept = user.isAdmin() ? null : user.departmentId();
        return build(name, dept, wardId, from, to, false);
    }

    private DashboardSummaryResponse build(String scope, Long dept, Long ward, LocalDate from, LocalDate to, boolean includeDepartments) {
        LocalDate f = from == null ? LocalDate.now(IST).minusDays(29) : from;
        LocalDate t = to == null ? LocalDate.now(IST) : to;
        if (t.isBefore(f)) throw new IllegalArgumentException("'to' must not be before 'from'");
        if (f.plusDays(366).isBefore(t)) throw new IllegalArgumentException("The date range can be at most one year");
        Instant fromI = f.atStartOfDay(IST).toInstant();
        Instant toI = t.plusDays(1).atStartOfDay(IST).toInstant();
        return new DashboardSummaryResponse(scope, f.toString(), t.toString(), repo.totals(dept, ward, fromI, toI),
                repo.byCategory(dept, ward, fromI, toI), repo.byStatus(dept, ward, fromI, toI),
                ward == null ? repo.byWard(dept, fromI, toI) : List.of(),
                // admin: every department; officer: only their own row (no comparison data about other departments)
                includeDepartments ? repo.departmentPerformance(fromI, toI)
                        : dept != null && ward == null ? repo.departmentPerformance(fromI, toI).stream()
                          .filter(d -> d.departmentId() == dept).toList() : List.of(),
                repo.dailyTrend(dept, ward, fromI, toI), ward == null && dept == null ? geo.hotspots(250, 2) : List.of());
    }
}
