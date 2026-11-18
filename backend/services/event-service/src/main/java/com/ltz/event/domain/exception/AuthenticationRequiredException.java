package com.ltz.event.domain.exception;

/** Doğrulanmış kimlik yoktur ya da kimlik claim'i geçersizdir (401). */
public class AuthenticationRequiredException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public AuthenticationRequiredException(String message) {
		super(message);
	}
}
