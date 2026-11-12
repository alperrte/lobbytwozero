package com.ltz.review.api.dto.request;

import com.ltz.review.domain.enums.ReportOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CloseReportsRequest(@NotNull ReportOutcome outcome, @NotBlank @Size(max = 500) String reason) {}
