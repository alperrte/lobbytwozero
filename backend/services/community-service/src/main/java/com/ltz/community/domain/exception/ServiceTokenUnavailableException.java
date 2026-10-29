package com.ltz.community.domain.exception;

/** Auth'tan servis token'ı alınamadı (gizli anahtar yok, kimlik reddedildi ya da Auth ulaşılamıyor). */
public class ServiceTokenUnavailableException extends RuntimeException {

	public ServiceTokenUnavailableException(String message) {
		super(message);
	}

	public ServiceTokenUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
