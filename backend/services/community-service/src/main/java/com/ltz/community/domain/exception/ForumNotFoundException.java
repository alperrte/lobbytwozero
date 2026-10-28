package com.ltz.community.domain.exception;

public class ForumNotFoundException extends RuntimeException {

	public ForumNotFoundException() {
		super("Forum not found.");
	}
}
