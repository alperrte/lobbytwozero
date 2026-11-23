package com.ltz.event.domain.exception;

/** Kullanıcının bu etkinlikte iptal edilebilir (ACTIVE) katılımı yoktur (404). */
public class ParticipationNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ParticipationNotFoundException() {
		super("Participation not found.");
	}
}
