package com.jansunwai.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(@Size(max = 120) String fullName, Boolean active) { }
