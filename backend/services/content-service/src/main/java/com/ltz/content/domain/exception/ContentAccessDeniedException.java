package com.ltz.content.domain.exception;

public class ContentAccessDeniedException extends RuntimeException {

	public ContentAccessDeniedException() {
		super("Only the owner of the content can perform this action.");
	}
}
