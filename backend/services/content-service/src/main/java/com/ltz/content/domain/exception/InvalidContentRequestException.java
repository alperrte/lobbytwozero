package com.ltz.content.domain.exception;

/** İstek şekil olarak geçerli ama iş kuralına uymuyor (ör. değiştirilecek alan yok); mesajlar sabittir. */
public class InvalidContentRequestException extends RuntimeException {

	public InvalidContentRequestException(String message) {
		super(message);
	}

	public InvalidContentRequestException(String message, Throwable cause) {
		super(message, cause);
	}
}
