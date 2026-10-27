package com.ltz.community.domain.exception;

public class ForumSlugTakenException extends RuntimeException {

	public ForumSlugTakenException() {
		super("A forum with this slug already exists.");
	}
}
