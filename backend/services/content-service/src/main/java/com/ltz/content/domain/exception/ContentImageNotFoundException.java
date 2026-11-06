package com.ltz.content.domain.exception;

public class ContentImageNotFoundException extends RuntimeException {

	public ContentImageNotFoundException() {
		super("Content image not found.");
	}
}
