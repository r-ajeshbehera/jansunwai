package com.jansunwai.service;

import com.jansunwai.dto.request.*;
import com.jansunwai.dto.response.PageResponse;
import com.jansunwai.dto.response.ProfileResponse;
import com.jansunwai.dto.response.UserResponse;
import com.jansunwai.entity.Department;
import com.jansunwai.entity.Role;
import com.jansunwai.entity.User;
import com.jansunwai.exception.BusinessRuleException;
import com.jansunwai.exception.ResourceNotFoundException;
import com.jansunwai.repository.DepartmentRepository;
import com.jansunwai.repository.UserRepository;
import com.jansunwai.security.AppUserPrincipal;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserService {
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserService(UserRepository users, DepartmentRepository departments, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.departments = departments;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ProfileResponse me(AppUserPrincipal p) {
        return ProfileResponse.from(users.findWithDepartment(p.id()).orElseThrow());
    }

    @Transactional
    public ProfileResponse updateProfile(UpdateProfileRequest req, AppUserPrincipal p) {
        User u = users.findWithDepartment(p.id()).orElseThrow();
        if (u.getRole() == Role.CITIZEN && (req.phone() == null || req.phone().isBlank())) {
            throw new BusinessRuleException("PHONE_REQUIRED", "Citizens must keep a phone number for updates");
        }
        u.setFullName(req.fullName().trim());
        u.setPhone(req.phone());
        audit.log(p.id(), "PROFILE_UPDATED", "USER", p.id(), null);
        return ProfileResponse.from(u);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest req, AppUserPrincipal p) {
        User u = users.findById(p.id()).orElseThrow();
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw new BusinessRuleException("WRONG_PASSWORD", "Current password is incorrect");
        }
        u.setPasswordHash(encoder.encode(req.newPassword()));
        audit.log(p.id(), "PASSWORD_CHANGED", "USER", p.id(), null);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(Role role, Long departmentId, String q, Pageable pageable) {
        String query = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        return PageResponse.of(users.search(role, departmentId, query, pageable), UserResponse::from);
    }

    /** Admin creates staff. The DB CHECK also enforces "officers have a department, others do not". */
    @Transactional
    public UserResponse create(CreateUserRequest req, AppUserPrincipal admin) {
        if (req.role() == Role.CITIZEN) throw new BusinessRuleException("USE_REGISTRATION", "Citizens register themselves");
        boolean officer = req.role() == Role.FIELD_OFFICER || req.role() == Role.DEPARTMENT_OFFICER;
        if (officer && req.departmentId() == null) throw new BusinessRuleException("DEPARTMENT_REQUIRED", "Officers need a department");
        if (!officer && req.departmentId() != null) throw new BusinessRuleException("NO_DEPARTMENT", "Admins do not belong to a department");
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) throw new BusinessRuleException("EMAIL_TAKEN", "Email already in use");
        Department dept = req.departmentId() == null ? null : departments.findById(req.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department", req.departmentId()));
        User u = users.save(new User(req.fullName().trim(), email, req.phone(), encoder.encode(req.password()), req.role(), dept));
        audit.log(admin.id(), "USER_CREATED", "USER", u.getId(), req.role().name());
        return UserResponse.from(u);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest req, AppUserPrincipal admin) {
        User u = users.findWithDepartment(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        if (req.fullName() != null && !req.fullName().isBlank()) u.setFullName(req.fullName().trim());
        if (req.active() != null) {
            if (!req.active() && id.equals(admin.id())) throw new BusinessRuleException("SELF_DEACTIVATION", "You cannot deactivate yourself");
            u.setActive(req.active());
        }
        audit.log(admin.id(), "USER_UPDATED", "USER", id, req.active() == null ? null : "active=" + req.active());
        return UserResponse.from(u);
    }
}
