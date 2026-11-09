package com.ltz.event.domain.enums;

/**
 * Etkinlik türü (docs/database.md §3.12; DB'de {@code varchar} + CHECK). Tür başına özel kurallar EVT-02 ve sonrasında
 * eklenir; "oylama" türünün nasıl eşleneceği (OTHER mı, ayrı değer mi) EVT-02'de karara bağlanır.
 */
public enum EventType {
	TOURNAMENT,
	GAME_NIGHT,
	COMMUNITY,
	OTHER
}
