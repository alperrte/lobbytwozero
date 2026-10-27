package com.ltz.community.domain.exception;

public class ModeratorRequiredException extends RuntimeException {

	public ModeratorRequiredException() {
		super("Moderator privileges are required.");
	}
}
