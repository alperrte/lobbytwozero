package com.ltz.community.domain.exception;

/** Forum konusunun oyun ilişkisi, forumun oyunuyla çelişemez. */
public class ForumGameMismatchException extends RuntimeException {

	public ForumGameMismatchException(String message) {
		super(message);
	}
}
