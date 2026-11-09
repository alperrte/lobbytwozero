package com.ltz.event.domain.enums;

/**
 * Etkinlik türü (EVT-02; DB'de {@code varchar} + {@code ck_events_type} CHECK, V111/V112). Değerler CHECK ile birebir
 * aynı olmalıdır ({@code EventTypeTest} bunu denetler).
 *
 * <ul>
 *   <li>{@link #TOURNAMENT}: turnuva (EVT-18'de ek organizasyon bilgisi eklenecek).</li>
 *   <li>{@link #GAME_NIGHT}: oyun gecesi (EVT-19).</li>
 *   <li>{@link #COMMUNITY}: topluluk etkinliği.</li>
 *   <li>{@link #POLL}: oylama. Karar KD-2: ayrı bir etkinlik türüdür; oy verme davranışı EVT-20'de eklenir.</li>
 *   <li>{@link #OTHER}: diğer.</li>
 * </ul>
 *
 * <p>Türe özel alan veya kural bu görevde tanımlı değildir; tür şu an sınıflandırma amaçlıdır. Tür oluşturulduktan
 * sonra değiştirilebilir mi sorusu EVT-14 (güncelleme) kapsamındadır.
 */
public enum EventType {
	TOURNAMENT,
	GAME_NIGHT,
	COMMUNITY,
	POLL,
	OTHER
}
