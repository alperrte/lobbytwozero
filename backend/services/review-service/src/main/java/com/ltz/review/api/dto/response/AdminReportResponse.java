package com.ltz.review.api.dto.response;

import com.ltz.review.domain.enums.ReportReason;
import java.time.Instant;
import java.util.UUID;

/** Moderatör görünümündeki rapor. Raportör kimliği dönmez. {@code status}: RECEIVED, REVIEWED veya DISMISSED. */
public record AdminReportResponse(UUID id, ReportReason reason, String detail, String status, Instant createdAt, Instant closedAt) {}
