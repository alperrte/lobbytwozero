package com.ltz.community.domain.exception;

/** Oyuna bağlı türlü içerik (GAME-11) kuralı ihlali: başlık/oyun zorunlulukları, forum ve repost kısıtları (422). */
public class GameContentRuleException extends RuntimeException {

	public GameContentRuleException(String message) {
		super(message);
	}
}
