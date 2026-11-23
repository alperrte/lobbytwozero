package com.ltz.event.domain.enums;

/** Katılım kaydı durumu (EVT-08/09). Yalnız {@link #ACTIVE} ve {@link #ATTENDED} kapasiteyi tutar. */
public enum ParticipationStatus {
	ACTIVE,
	CANCELLED_BY_USER,
	CANCELLED_BY_EVENT,
	ATTENDED
}
