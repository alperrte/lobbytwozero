package com.ltz.event.domain.exception;

/**
 * Arama metni, Game Service'te işlenebilir üst sınırdan fazla oyunla eşleşiyor (422). Eksik sonuç üretmemek için
 * sonuç kümesi kırpılmaz; kullanıcıdan aramayı daraltması istenir.
 */
public class GameSearchTooBroadException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public static final String CODE = "GAME_SEARCH_TOO_BROAD";

	private final int maxGames;

	public GameSearchTooBroadException(int maxGames) {
		super("The search matches too many games. Use a longer search text.");
		this.maxGames = maxGames;
	}

	public int getMaxGames() {
		return maxGames;
	}
}
