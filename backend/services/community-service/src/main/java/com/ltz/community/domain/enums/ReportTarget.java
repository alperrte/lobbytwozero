package com.ltz.community.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Raporlanabilir içerik türü. */
public enum ReportTarget {
	POST,
	COMMENT;

	@JsonCreator
	public static ReportTarget fromJson(String value) {
		return StrictEnums.exact(ReportTarget.class, value);
	}
}
