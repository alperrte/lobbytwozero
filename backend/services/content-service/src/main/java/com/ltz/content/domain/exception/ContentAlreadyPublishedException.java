package com.ltz.content.domain.exception;

public class ContentAlreadyPublishedException extends RuntimeException {

	public ContentAlreadyPublishedException() {
		super("The content is already published.");
	}
}
