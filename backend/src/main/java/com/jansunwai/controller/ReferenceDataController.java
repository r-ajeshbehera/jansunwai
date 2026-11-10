package com.jansunwai.controller;

import com.jansunwai.dto.request.DepartmentRequest;
import com.jansunwai.dto.request.WardRequest;
import com.jansunwai.dto.response.*;
import com.jansunwai.entity.ComplaintStatus;
import com.jansunwai.exception.ResourceNotFoundException;
import com.jansunwai.repository.GeoRepository;
import com.jansunwai.security.AppUserPrincipal;
import com.jansunwai.security.CurrentUser;
import com.jansunwai.service.AssignmentService;
import com.jansunwai.service.ComplaintService;
import com.jansunwai.service.GeoService;
import com.jansunwai.service.ReferenceDataService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Departments & wards")
public class ReferenceDataController {
    private final ReferenceDataService reference;
    private final ComplaintService complaints;
    private final AssignmentService assignments;
    private final GeoService geo;

    public ReferenceDataController(ReferenceDataService reference, ComplaintService complaints, AssignmentService assignments,
                                   GeoService geo) {
        this.reference = reference;
        this.complaints = complaints;
        this.assignments = assignments;
        this.geo = geo;
    }

    @GetMapping("/departments")
    public List<DepartmentResponse> departments() {
        return reference.departments();
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public DepartmentResponse createDepartment(@Valid @RequestBody DepartmentRequest req) {
        return reference.saveDepartment(null, req, CurrentUser.get());
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DepartmentResponse updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentRequest req) {
        return reference.saveDepartment(id, req, CurrentUser.get());
    }

    @GetMapping("/departments/{id}/complaints")
    @PreAuthorize("hasAnyRole('DEPARTMENT_OFFICER','ADMIN')")
    public PageResponse<ComplaintSummary> departmentComplaints(@PathVariable Long id,
                                                               @RequestParam(required = false) ComplaintStatus status,
                                                               @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        AppUserPrincipal user = CurrentUser.get();
        if (!user.isAdmin() && !id.equals(user.departmentId())) throw new ResourceNotFoundException("Department", id);
        return complaints.list(user, status, id, null, false, null, Pages.capped(pageable));
    }

    @GetMapping("/departments/{id}/field-officers")
    @PreAuthorize("hasAnyRole('DEPARTMENT_OFFICER','ADMIN')")
    public List<UserResponse> fieldOfficers(@PathVariable Long id) {
        AppUserPrincipal user = CurrentUser.get();
        if (!user.isAdmin() && !id.equals(user.departmentId())) throw new ResourceNotFoundException("Department", id);
        return assignments.fieldOfficers(id, user);
    }

    @GetMapping("/wards")
    public List<WardResponse> wards() {
        return reference.wards();
    }

    @GetMapping("/wards/shapes")
    public List<GeoRepository.WardShape> wardShapes() {
        return geo.wardShapes();
    }

    @PostMapping("/wards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public WardResponse createWard(@Valid @RequestBody WardRequest req) {
        return reference.createWard(req, CurrentUser.get());
    }
}
