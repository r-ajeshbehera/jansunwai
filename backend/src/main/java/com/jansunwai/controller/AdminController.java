package com.jansunwai.controller;

import com.jansunwai.dto.request.CreateUserRequest;
import com.jansunwai.dto.request.UpdateUserRequest;
import com.jansunwai.dto.response.AuditResponse;
import com.jansunwai.dto.response.PageResponse;
import com.jansunwai.dto.response.UserResponse;
import com.jansunwai.entity.Role;
import com.jansunwai.repository.AuditLogRepository;
import com.jansunwai.security.CurrentUser;
import com.jansunwai.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Administration")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserService users;
    private final AuditLogRepository audit;

    public AdminController(UserService users, AuditLogRepository audit) {
        this.users = users;
        this.audit = audit;
    }

    @GetMapping("/users")
    public PageResponse<UserResponse> users(@RequestParam(required = false) Role role, @RequestParam(required = false) Long departmentId,
                                            @RequestParam(required = false) String q,
                                            @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {
        return users.search(role, departmentId, q, Pages.capped(pageable));
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest req) {
        return users.create(req, CurrentUser.get());
    }

    @PatchMapping("/users/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req) {
        return users.update(id, req, CurrentUser.get());
    }

    @GetMapping("/audit-logs")
    @Transactional(readOnly = true)
    public PageResponse<AuditResponse> auditLogs(@RequestParam(required = false) String entityType,
                                                 @RequestParam(required = false) Long actorId,
                                                 @PageableDefault(size = 50) Pageable pageable) {
        return PageResponse.of(audit.search(entityType, actorId, Pages.capped(pageable)), AuditResponse::from);
    }
}
