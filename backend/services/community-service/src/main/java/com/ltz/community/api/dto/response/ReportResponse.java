package com.ltz.community.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.community.domain.enums.ReportReason;
import com.ltz.community.domain.enums.ReportStatus;
import com.ltz.community.domain.enums.ReportTarget;

/** Raporlayana dönen kayıt; içerik yazarı ve diğer raporlayanlar açığa çıkmaz. */
public record ReportResponse(
		UUID id,
		ReportTarget targetType,
		UUID targetId,
		ReportReason reason,
		ReportStatus status,
		Instant createdAt) {
}
