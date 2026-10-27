package com.ltz.community.domain.exception;

/** Akış sorgusu parametresi geçersiz (ör. bozuk cursor); mesajlar sabittir, kullanıcı girdisi içermez. */
public class InvalidPostQueryException extends RuntimeException {

	public InvalidPostQueryException(String message) {
		super(message);
	}
}
