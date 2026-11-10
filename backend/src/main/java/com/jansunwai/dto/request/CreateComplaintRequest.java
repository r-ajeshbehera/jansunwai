package com.jansunwai.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/** Location and ward are optional: a complaint without GPS is still accepted (ward can be picked from a list). */
public record CreateComplaintRequest(
        @NotBlank @Size(min = 5, max = 150) String title,
        @NotBlank @Size(min = 10, max = 4000) String description,
        @Pattern(regexp = "en|hi|or", message = "must be en, hi or or") String language,
        Long wardId,
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
        @Size(max = 200) String landmark) { }
