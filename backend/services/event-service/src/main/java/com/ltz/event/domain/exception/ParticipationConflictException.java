package com.ltz.event.domain.exception;

/** Katılım isteği mevcut durumla çelişir (409). {@link Reason} istemcinin ayırt edebileceği sabit koddur. */
public class ParticipationConflictException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public enum Reason {
		ALREADY_JOINED("You have already joined this event."),
		EVENT_NOT_OPEN("This event is not open for participation."),
		EVENT_FULL("This event has reached its capacity."),
		ORGANIZER_CANNOT_JOIN("You cannot join your own event.");

		private final String message;

		Reason(String message) {
			this.message = message;
		}

		String message() {
			return message;
		}
	}

	private final Reason reason;

	public ParticipationConflictException(Reason reason) {
		super(reason.message());
		this.reason = reason;
	}

	public Reason getReason() {
		return reason;
	}
}
