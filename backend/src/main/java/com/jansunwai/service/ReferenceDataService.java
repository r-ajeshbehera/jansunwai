package com.jansunwai.service;

import com.jansunwai.dto.request.DepartmentRequest;
import com.jansunwai.dto.request.WardRequest;
import com.jansunwai.dto.response.DepartmentResponse;
import com.jansunwai.dto.response.WardResponse;
import com.jansunwai.entity.Department;
import com.jansunwai.entity.Ward;
import com.jansunwai.exception.BusinessRuleException;
import com.jansunwai.exception.ResourceNotFoundException;
import com.jansunwai.repository.DepartmentRepository;
import com.jansunwai.repository.GeoRepository;
import com.jansunwai.repository.WardRepository;
import com.jansunwai.security.AppUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReferenceDataService {
    private final DepartmentRepository departments;
    private final WardRepository wards;
    private final GeoRepository geo;
    private final AuditService audit;

    public ReferenceDataService(DepartmentRepository departments, WardRepository wards, GeoRepository geo, AuditService audit) {
        this.departments = departments;
        this.wards = wards;
        this.geo = geo;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> departments() {
        return departments.findAllByOrderByName().stream().map(DepartmentResponse::from).toList();
    }

    @Transactional
    public DepartmentResponse saveDepartment(Long id, DepartmentRequest req, AppUserPrincipal admin) {
        Department d;
        if (id == null) {
            if (departments.existsByCode(req.code())) throw new BusinessRuleException("CODE_TAKEN", "Department code already exists");
            d = departments.save(new Department(req.code(), req.name().trim()));
        } else {
            d = departments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department", id));
            d.setName(req.name().trim());
            if (req.active() != null) d.setActive(req.active());
        }
        audit.log(admin.id(), id == null ? "DEPARTMENT_CREATED" : "DEPARTMENT_UPDATED", "DEPARTMENT", d.getId(), d.getCode());
        return DepartmentResponse.from(d);
    }

    @Transactional(readOnly = true)
    public List<WardResponse> wards() {
        return wards.findAllByOrderByCode().stream().map(WardResponse::from).toList();
    }

    @Transactional
    public WardResponse createWard(WardRequest req, AppUserPrincipal admin) {
        if (wards.existsByCode(req.code())) throw new BusinessRuleException("CODE_TAKEN", "Ward code already exists");
        Ward w = wards.saveAndFlush(new Ward(req.code().trim(), req.name().trim()));
        if (req.boundary() != null) geo.setWardBoundary(w.getId(), req.boundary());
        audit.log(admin.id(), "WARD_CREATED", "WARD", w.getId(), w.getCode());
        return WardResponse.from(w);
    }
}
