package com.ltz.community.api.controller;

/**
 * Servisler arası (iç) uçlar. Gateway yalnız {@code /api/v1/<servis>/**} yollarını route eder; bu yollar dışarıya
 * açılmaz ve yalnız Auth'un imzaladığı servis token'ıyla ({@code svc+jwt}) çağrılabilir.
 */
public final class InternalApiPaths {

	public static final String BASE = "/internal";
	/** Auth → community hesap verisi silme (D4 sözleşmesi). */
	public static final String ACCOUNT_ERASURE = BASE + "/v1/accounts/{accountId}";
	/** Diğer servislerin community'ye ilettiği olaylar (docs/architecture.md §6.2). */
	public static final String EVENTS = BASE + "/events";

	private InternalApiPaths() {
	}
}
