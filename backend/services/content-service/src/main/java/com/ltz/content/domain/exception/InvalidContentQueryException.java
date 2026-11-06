package com.ltz.content.domain.exception;

/** Liste/arama sorgusu geçersiz (bozuk imleç, ters tarih aralığı, çok kısa arama metni); mesajlar sabittir. */
public class InvalidContentQueryException extends RuntimeException {

	public InvalidContentQueryException(String message) {
		super(message);
	}
}
