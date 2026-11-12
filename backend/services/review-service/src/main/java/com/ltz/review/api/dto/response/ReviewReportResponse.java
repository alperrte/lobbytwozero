package com.ltz.review.api.dto.response;

import com.ltz.review.domain.enums.ReportReason;
import java.time.Instant;
import java.util.UUID;

/**
 * Raportörün kendi raporu. Raportör ve inceleme yazarı kimliği, moderasyon notu veya karar dönmez. {@code status}
 * yalnız Review'ın yerel kaydıdır (RECEIVED); Admin'e iletildiği veya karar verildiği anlamına gelmez.
 */
public record ReviewReportResponse(UUID id, UUID reviewId, ReportReason reason, String detail, String status, Instant createdAt) {}
