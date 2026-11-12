package com.ltz.review.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Yönetim listesi öğesi: raporlanmış inceleme ve rapor sayıları. Yazar ve raportör kimliği dönmez; silinmiş incelemenin metni yoktur. */
public record AdminReportedReviewResponse(UUID reviewId, UUID gameId, String reviewStatus, String body, long reportCount,
        long totalReportCount, Instant latestReportAt) {}
