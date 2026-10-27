package com.ltz.community.domain.exception;

public class ReportingOwnContentException extends RuntimeException {

	public ReportingOwnContentException() {
		super("You cannot report your own content.");
	}
}
