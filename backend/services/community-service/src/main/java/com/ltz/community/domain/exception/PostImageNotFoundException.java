package com.ltz.community.domain.exception;

public class PostImageNotFoundException extends RuntimeException {

	public PostImageNotFoundException() {
		super("Image not found.");
	}
}
