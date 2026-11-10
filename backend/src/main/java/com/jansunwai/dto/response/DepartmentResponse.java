package com.jansunwai.dto.response;

import com.jansunwai.entity.Department;

public record DepartmentResponse(Long id, String code, String name, boolean active) {
    public static DepartmentResponse from(Department d) {
        return new DepartmentResponse(d.getId(), d.getCode(), d.getName(), d.isActive());
    }
}
