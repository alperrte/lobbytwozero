package com.ltz.community.domain.exception;

public class TooManyGameContentException extends RuntimeException {

	public TooManyGameContentException() {
		super("Too many game contents created in a short time. Try again later.");
	}
}
