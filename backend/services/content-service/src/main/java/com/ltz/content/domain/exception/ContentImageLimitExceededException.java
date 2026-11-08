package com.ltz.content.domain.exception;

public class ContentImageLimitExceededException extends RuntimeException {

	public ContentImageLimitExceededException(int limit) {
		super("A content can have at most " + limit + " images.");
	}
}
