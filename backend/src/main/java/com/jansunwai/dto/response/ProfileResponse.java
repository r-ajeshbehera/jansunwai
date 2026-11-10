package com.jansunwai.dto.response;

import com.jansunwai.entity.Role;
import com.jansunwai.entity.User;

/** The caller's OWN profile: the only user DTO that contains a phone number. */
public record ProfileResponse(Long id, String fullName, String email, String phone, Role role, Long departmentId,
                              String departmentName) {
    public static ProfileResponse from(User u) {
        return new ProfileResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(), u.getDepartmentId(),
                u.getDepartment() == null ? null : u.getDepartment().getName());
    }
}
