package com.ltz.community.domain.exception;

public class GameNotAvailableException extends RuntimeException {

	public GameNotAvailableException() {
		super("The referenced game does not exist or is not available.");
	}
}
