package com.ltz.content.domain.exception;

/** Aynı yazarın aynı türde, aynı metne sahip etkin bir içeriği zaten var (mükerrer paylaşım). */
public class DuplicateContentException extends RuntimeException {

	public DuplicateContentException() {
		super("You already have content of this type with the same text.");
	}

	public DuplicateContentException(Throwable cause) {
		super("You already have content of this type with the same text.", cause);
	}
}
