package com.ltz.community.domain.exception;

public class CommentAccessDeniedException extends RuntimeException {

	public CommentAccessDeniedException() {
		super("Only the author of the comment can perform this action.");
	}
}
