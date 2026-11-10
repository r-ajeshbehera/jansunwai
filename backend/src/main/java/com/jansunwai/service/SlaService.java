package com.jansunwai.service;

import com.jansunwai.dto.request.SlaConfigRequest;
import com.jansunwai.dto.response.SlaConfigResponse;
import com.jansunwai.entity.Complaint;
import com.jansunwai.entity.Department;
import com.jansunwai.entity.Priority;
import com.jansunwai.entity.SlaConfiguration;
import com.jansunwai.exception.BusinessRuleException;
import com.jansunwai.exception.ResourceNotFoundException;
import com.jansunwai.repository.DepartmentRepository;
import com.jansunwai.repository.SlaConfigurationRepository;
import com.jansunwai.security.AppUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Deadline rules: department-specific policy if one exists, otherwise the default policy for that priority. */
@Service
public class SlaService {
    private final SlaConfigurationRepository configs;
    private final DepartmentRepository departments;
    private final AuditService audit;

    public SlaService(SlaConfigurationRepository configs, DepartmentRepository departments, AuditService audit) {
        this.configs = configs;
        this.departments = departments;
        this.audit = audit;
    }

    public record Policy(int resolutionHours, int warningPercent) { }

    @Transactional(readOnly = true)
    public Policy policyFor(Long departmentId, Priority priority) {
        return configs.findForDepartment(departmentId, priority)
                .or(() -> configs.findDefault(priority))
                .map(c -> new Policy(c.getResolutionHours(), c.getWarningPercent()))
                .orElseThrow(() -> new BusinessRuleException("SLA_MISSING", "No SLA policy configured for priority " + priority));
    }

    public static Instant dueAt(Instant acceptedAt, int resolutionHours) {
        return acceptedAt.plus(Duration.ofHours(resolutionHours));
    }

    /** Moment when "deadline approaching" fires, e.g. 75% of the way from acceptance to the deadline. */
    public static Instant warningAt(Instant acceptedAt, Instant dueAt, int warningPercent) {
        long total = Duration.between(acceptedAt, dueAt).toSeconds();
        return acceptedAt.plusSeconds(total * warningPercent / 100);
    }

    public boolean isWarningDue(Complaint c, Instant now) {
        if (c.getAcceptedAt() == null || c.getSlaDueAt() == null || c.getPriority() == null) return false;
        Policy p = policyFor(c.getDepartmentId(), c.getPriority());
        return !now.isBefore(warningAt(c.getAcceptedAt(), c.getSlaDueAt(), p.warningPercent())) && now.isBefore(c.getSlaDueAt());
    }

    @Transactional(readOnly = true)
    public List<SlaConfigResponse> list() {
        return configs.findAllOrdered().stream().map(SlaConfigResponse::from).toList();
    }

    /** Create or update the policy for (department or default, priority). Existing deadlines are not changed. */
    @Transactional
    public SlaConfigResponse save(SlaConfigRequest req, AppUserPrincipal admin) {
        Department dept = req.departmentId() == null ? null : departments.findById(req.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department", req.departmentId()));
        SlaConfiguration cfg = (req.departmentId() == null ? configs.findDefault(req.priority())
                : configs.findForDepartment(req.departmentId(), req.priority()))
                .orElse(null);
        boolean active = req.active() == null || req.active();
        if (cfg == null) {
            if (req.departmentId() != null && configs.existsFor(req.departmentId(), req.priority())) {
                throw new BusinessRuleException("SLA_EXISTS", "An inactive policy exists; update it instead");
            }
            cfg = configs.save(new SlaConfiguration(dept, req.priority(), req.resolutionHours(), req.warningPercent()));
        } else {
            if (req.departmentId() == null && !active) {
                throw new BusinessRuleException("DEFAULT_REQUIRED", "Default policies cannot be deactivated");
            }
            cfg.update(req.resolutionHours(), req.warningPercent(), active);
        }
        audit.log(admin.id(), "SLA_CONFIG_SAVED", "SLA_CONFIGURATION", cfg.getId(),
                (dept == null ? "default" : dept.getCode()) + " " + req.priority() + "=" + req.resolutionHours() + "h");
        return SlaConfigResponse.from(cfg);
    }
}
