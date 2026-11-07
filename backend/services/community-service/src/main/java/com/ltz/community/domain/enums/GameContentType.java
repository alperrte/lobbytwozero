package com.ltz.community.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Oyuna bağlı topluluk içeriği türleri (GAME-11). Adlar Game Service'in modül türleriyle
 * ({@code GET /api/v1/game/games/{gameId}/modules/{type}}) birebir aynıdır. {@code posts.category}'den bağımsızdır;
 * veritabanında varchar + CHECK olarak tutulur.
 */
public enum GameContentType {
	GUIDES,
	EASTER_EGGS,
	EXPERIENCES;

	/** JSON'dan yalnız tam (büyük harfli) adla kabul edilir; bkz. {@link StrictEnums}. */
	@JsonCreator
	public static GameContentType fromJson(String value) {
		return StrictEnums.exact(GameContentType.class, value);
	}
}
