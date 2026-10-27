package com.ltz.community.domain.exception;

public class CommentNotFoundException extends RuntimeException {

	public CommentNotFoundException() {
		super("Comment not found.");
	}
}
