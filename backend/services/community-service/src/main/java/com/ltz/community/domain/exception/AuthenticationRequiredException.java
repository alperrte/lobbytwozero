package com.ltz.community.domain.exception;

public class AuthenticationRequiredException extends RuntimeException {

	public AuthenticationRequiredException(String message) {
		super(message);
	}
}
