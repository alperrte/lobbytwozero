package com.ltz.community.domain.exception;

public class TooManyReportsException extends RuntimeException {

	public TooManyReportsException() {
		super("Too many reports in a short time. Try again later.");
	}
}
