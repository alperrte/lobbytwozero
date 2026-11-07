package com.ltz.content.api.controller;

/**
 * Servisler arası (iç) uçlar. Gateway yalnız {@code /api/v1/<servis>/**} yollarını route eder; bu yollar dışarıya
 * açılmaz ve yalnız Auth'un imzaladığı servis token'ıyla ({@code svc+jwt}) çağrılabilir.
 */
public final class InternalApiPaths {

	public static final String BASE = "/internal";

	/** Diğer servislerin content-service'e ilettiği olaylar (docs/architecture.md §6.2). */
	public static final String EVENTS = BASE + "/events";

	private InternalApiPaths() {
	}
}
