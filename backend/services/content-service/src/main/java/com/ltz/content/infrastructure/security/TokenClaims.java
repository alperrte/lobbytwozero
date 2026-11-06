package com.ltz.content.infrastructure.security;

/**
 * Auth Service'in token sözleşmesindeki (docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md §3) bu servisin kullandığı sabitler.
 * Değerlerin sahibi Auth'tur; bu servis onlara uyar, değiştirmez.
 */
public final class TokenClaims {

	/** JWT başlığı {@code typ}: kullanıcı access token'ı. Servis token'ı ({@code svc+jwt}) bu serviste kabul edilmez. */
	public static final String ACCESS_TOKEN_TYPE = "at+jwt";
	/** Kullanıcı token'ında rol listesi, öneksiz ad ({@code PLAYER}, {@code MODERATOR}, {@code ADMIN} ...). */
	public static final String ROLES = "roles";
	/** Resmî duyuru oluşturabilen rol. */
	public static final String ADMIN_ROLE = "ADMIN";

	private TokenClaims() {
	}
}
