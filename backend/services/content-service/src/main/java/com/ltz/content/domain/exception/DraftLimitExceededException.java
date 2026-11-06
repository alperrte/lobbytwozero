package com.ltz.content.domain.exception;

public class DraftLimitExceededException extends RuntimeException {

	public DraftLimitExceededException(int limit) {
		super("You can have at most " + limit + " drafts. Delete one first.");
	}
}
