package com.ltz.community.domain.exception;

public class PostNotEditableException extends RuntimeException {

	public PostNotEditableException() {
		super("Post cannot be edited in its current state.");
	}
}
