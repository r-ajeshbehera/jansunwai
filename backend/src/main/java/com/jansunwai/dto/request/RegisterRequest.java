package com.jansunwai.dto.request;

import jakarta.validation.constraints.*;

/** Public registration. There is deliberately NO role field: public sign-up always creates a CITIZEN. */
public record RegisterRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "must be a 10-digit Indian mobile number") String phone,
        @NotBlank @Size(min = 8, max = 72) String password) { }
