package com.ltz.event.domain.exception;

/** İstenen etkinlik yoktur (404). */
public class EventNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public EventNotFoundException() {
		super("Event not found.");
	}
}
