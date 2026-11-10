package com.jansunwai.dto.request;

import com.jansunwai.entity.RemarkVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** infoRequest = "please send more information" to the citizen (always PUBLIC). Citizens can only write PUBLIC remarks. */
public record RemarkRequest(@NotBlank @Size(max = 1000) String text, @NotNull RemarkVisibility visibility, boolean infoRequest) { }
