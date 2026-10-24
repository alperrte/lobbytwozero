package com.ltz.community.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Sabit rapor gerekçesi listesi; ayrıntı serbest metinle ({@code detail}) tamamlanabilir. */
public enum ReportReason {
	SPAM,
	HARASSMENT,
	HATE_SPEECH,
	SEXUAL_CONTENT,
	VIOLENCE,
	ILLEGAL_CONTENT,
	MISINFORMATION,
	OTHER;

	@JsonCreator
	public static ReportReason fromJson(String value) {
		return StrictEnums.exact(ReportReason.class, value);
	}
}
