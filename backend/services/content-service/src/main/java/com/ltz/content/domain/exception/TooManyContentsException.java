package com.ltz.content.domain.exception;

public class TooManyContentsException extends RuntimeException {

	public TooManyContentsException() {
		super("Too many contents created in a short time. Try again later.");
	}
}
