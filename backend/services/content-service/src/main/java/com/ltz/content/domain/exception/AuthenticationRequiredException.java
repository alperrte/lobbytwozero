package com.ltz.content.domain.exception;

public class AuthenticationRequiredException extends RuntimeException {

	public AuthenticationRequiredException(String message) {
		super(message);
	}
}
