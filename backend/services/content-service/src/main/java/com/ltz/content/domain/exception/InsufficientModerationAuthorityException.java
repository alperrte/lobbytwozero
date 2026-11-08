package com.ltz.content.domain.exception;

/**
 * Moderasyon kararını veren yetkinin bu içerik için yeterli olmadığını bildirir (ör. resmî duyuruya {@code MODERATOR}
 * kararı). Olay işlenmiş sayılmaz; içerik, geçmiş ve inbox değişmez. Mesaj sabittir.
 */
public class InsufficientModerationAuthorityException extends RuntimeException {

	public InsufficientModerationAuthorityException() {
		super("The deciding authority is not sufficient for this content.");
	}
}
