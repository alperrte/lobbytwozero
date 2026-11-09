package com.ltz.event.domain.enums;

/**
 * Etkinlik durumu (docs/architecture.md §8). Yeni etkinlik her zaman {@link #PLANNED} başlar; durum geçişleri (EVT-16)
 * ve iptal (EVT-15) ilgili görevlerde eklenir.
 */
public enum EventStatus {
	PLANNED,
	ACTIVE,
	COMPLETED,
	CANCELLED
}
