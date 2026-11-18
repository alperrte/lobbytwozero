package com.ltz.event.domain.exception;

/** Etkinlik zamanı kurala aykırıdır (başlangıç geçmişte ya da bitiş başlangıçtan sonra değil). */
public class InvalidEventScheduleException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public enum Reason {
		STARTS_IN_THE_PAST("startsAt must be in the future."),
		ENDS_NOT_AFTER_START("endsAt must be after startsAt.");

		private final String message;

		Reason(String message) {
			this.message = message;
		}

		String message() {
			return message;
		}
	}

	private final Reason reason;

	public InvalidEventScheduleException(Reason reason) {
		super(reason.message());
		this.reason = reason;
	}

	public Reason getReason() {
		return reason;
	}
}
