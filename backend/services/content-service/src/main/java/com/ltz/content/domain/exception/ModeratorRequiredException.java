package com.ltz.content.domain.exception;

/** Moderasyon işlemi yalnız moderatör ({@code MODERATOR}) ya da yönetici ({@code ADMIN}) rolüyle yapılır. */
public class ModeratorRequiredException extends RuntimeException {

	public ModeratorRequiredException() {
		super("Only moderators or administrators can perform this action.");
	}
}
