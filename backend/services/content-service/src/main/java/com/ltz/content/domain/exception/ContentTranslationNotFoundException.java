package com.ltz.content.domain.exception;

public class ContentTranslationNotFoundException extends RuntimeException {

	public ContentTranslationNotFoundException() {
		super("Content translation not found.");
	}
}
