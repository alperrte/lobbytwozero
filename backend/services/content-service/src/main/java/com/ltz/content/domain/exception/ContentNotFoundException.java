package com.ltz.content.domain.exception;

public class ContentNotFoundException extends RuntimeException {

	public ContentNotFoundException() {
		super("Content not found.");
	}
}
