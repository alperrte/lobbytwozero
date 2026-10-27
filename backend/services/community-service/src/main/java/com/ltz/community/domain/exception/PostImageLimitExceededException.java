package com.ltz.community.domain.exception;

public class PostImageLimitExceededException extends RuntimeException {

	public PostImageLimitExceededException(int limit) {
		super("A post can have at most " + limit + " images.");
	}
}
