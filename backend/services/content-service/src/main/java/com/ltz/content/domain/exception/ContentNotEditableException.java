package com.ltz.content.domain.exception;

public class ContentNotEditableException extends RuntimeException {

	public ContentNotEditableException() {
		super("Content cannot be modified in its current state.");
	}
}
