package com.ltz.event.domain.exception;

/** Kimliği doğrulanmış kullanıcı etkinlik yönetmeye (oluşturmaya) yetkili bir role sahip değildir (403). */
public class EventCreationForbiddenException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public EventCreationForbiddenException() {
		super("Access is denied.");
	}
}
