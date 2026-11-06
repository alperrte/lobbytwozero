package com.ltz.content.domain.exception;

/** Servisler arası olay zarfı ya da yükü geçersiz (üretici-tür uyuşmazlığı, eksik alan, bilinmeyen eylem). Mesaj sabittir. */
public class InvalidInboundEventException extends RuntimeException {

	public InvalidInboundEventException(String message) {
		super(message);
	}
}
