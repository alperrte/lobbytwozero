package com.ltz.community.domain.exception;

/** Gelen olay zarfı ya da yükü geçersiz (bilinmeyen üretici, eksik/bozuk alan). */
public class InvalidInboundEventException extends RuntimeException {

	public InvalidInboundEventException(String message) {
		super(message);
	}
}
