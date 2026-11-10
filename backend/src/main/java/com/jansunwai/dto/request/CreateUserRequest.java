package com.jansunwai.dto.request;

import com.jansunwai.entity.Role;
import jakarta.validation.constraints.*;

public record CreateUserRequest(@NotBlank @Size(max = 120) String fullName, @NotBlank @Email @Size(max = 160) String email,
                                @Pattern(regexp = "^[6-9]\\d{9}$", message = "must be a 10-digit Indian mobile number") String phone,
                                @NotBlank @Size(min = 8, max = 72) String password, @NotNull Role role, Long departmentId) { }
