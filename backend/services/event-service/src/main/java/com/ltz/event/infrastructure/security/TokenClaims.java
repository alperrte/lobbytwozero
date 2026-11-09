package com.ltz.event.infrastructure.security;

/**
 * Auth Service'in token sözleşmesinden (docs-auth/servis-sozlesmeleri.md §1-§3) bu servisin kullandığı sabitler.
 * Değerlerin sahibi Auth'tur; bu servis onlara uyar, değiştirmez.
 */
public final class TokenClaims {

	/** JWT başlığı {@code typ}: kullanıcı access token'ı. Servis token'ı ({@code svc+jwt}) kullanıcı uçlarında geçmez. */
	public static final String ACCESS_TOKEN_TYPE = "at+jwt";
	/** Kullanıcı token'ında rol listesi, öneksiz ad ({@code PLAYER}, {@code MODERATOR}, {@code ADMIN} ...). */
	public static final String ROLES = "roles";

	private TokenClaims() {
	}
}
