package com.ltz.event.application.service;

/**
 * Arama metnini güvenli bir LIKE desenine çevirir (EVT-05). Kullanıcının yazdığı {@code %}, {@code _} ve kaçış
 * karakteri ({@code !}) joker olarak değil <b>düz metin</b> olarak aranır; böylece "100%" yalnız "100%" içeren
 * başlıkları bulur ve kullanıcı sorguyu genişletemez. Sorgu tarafında {@code escape '!'} kullanılır.
 */
final class EventSearch {

	static final char ESCAPE = '!';

	private EventSearch() {
	}

	/** {@code %metin%} biçiminde "içerir" deseni üretir. */
	static String containsPattern(String text) {
		StringBuilder pattern = new StringBuilder(text.length() + 8).append('%');
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == ESCAPE || c == '%' || c == '_') {
				pattern.append(ESCAPE);
			}
			pattern.append(c);
		}
		return pattern.append('%').toString();
	}
}
