package com.ltz.event.domain.exception;

/** Etkinliğin bağlanmak istendiği oyun LTZ oyun kataloğunda yoktur veya yayında değildir (422). */
public class GameNotAvailableException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public GameNotAvailableException() {
		super("The referenced game does not exist or is not available.");
	}
}
