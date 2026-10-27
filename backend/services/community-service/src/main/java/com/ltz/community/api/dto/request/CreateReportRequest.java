package com.ltz.community.api.dto.request;

import com.ltz.community.domain.entity.Report;
import com.ltz.community.domain.enums.ReportReason;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code reason} sabit listeden zorunludur; {@code detail} isteğe bağlı kısa açıklamadır (düz metin). */
public record CreateReportRequest(
		@NotNull ReportReason reason,
		@Size(max = Report.DETAIL_MAX_LENGTH)
		@Pattern(regexp = Report.DETAIL_ALLOWED_CHARACTERS, message = "must not contain control characters")
		String detail) {
}
