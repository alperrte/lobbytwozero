package com.ltz.community.domain.exception;

public class AlreadyReportedException extends RuntimeException {

	public AlreadyReportedException() {
		super("You have already reported this content.");
	}
}
