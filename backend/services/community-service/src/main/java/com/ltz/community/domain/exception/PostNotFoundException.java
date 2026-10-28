package com.ltz.community.domain.exception;

public class PostNotFoundException extends RuntimeException {

	public PostNotFoundException() {
		super("Post not found.");
	}
}
