package com.jansunwai.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * reassign must be true to replace an existing assignment. Without it, assigning an already-assigned complaint is a
 * 409: two officers clicking "Assign" at the same moment can never both win, whatever the timing.
 */
public record AssignRequest(@NotNull Long fieldOfficerId, @Size(max = 300) String note, boolean reassign) { }
