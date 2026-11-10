package com.jansunwai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DepartmentRequest(@NotBlank @Pattern(regexp = "^[A-Z_]{3,30}$", message = "use UPPER_CASE letters and _") String code,
                                @NotBlank @Size(max = 100) String name, Boolean active) { }
