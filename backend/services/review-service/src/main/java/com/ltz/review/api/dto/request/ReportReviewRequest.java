package com.ltz.review.api.dto.request;

import com.ltz.review.domain.enums.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportReviewRequest(@NotNull ReportReason reason, @Size(max = 500) String detail) {}
