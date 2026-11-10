package com.jansunwai.dto.response;

import com.jansunwai.entity.Role;
import com.jansunwai.entity.User;

import java.time.Instant;

/** Admin / staff lists. Deliberately has NO phone field (data minimisation). */
public record UserResponse(Long id, String fullName, String email, Role role, Long departmentId, String departmentName,
                           boolean active, Instant createdAt) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.getDepartmentId(),
                u.getDepartment() == null ? null : u.getDepartment().getName(), u.isActive(), u.getCreatedAt());
    }
}
