package com.ltz.community.domain.exception;

public class PostAccessDeniedException extends RuntimeException {

	public PostAccessDeniedException() {
		super("Only the owner of the post can perform this action.");
	}
}
