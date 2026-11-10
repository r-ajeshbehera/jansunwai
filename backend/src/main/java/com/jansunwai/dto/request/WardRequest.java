package com.jansunwai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** boundary: optional ring of [longitude, latitude] points (at least 3) defining the ward polygon. */
public record WardRequest(@NotBlank @Size(max = 20) String code, @NotBlank @Size(max = 100) String name,
                          @Size(min = 3, max = 500) List<@Size(min = 2, max = 2) List<Double>> boundary) { }
