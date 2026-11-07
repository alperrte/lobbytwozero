package com.ltz.content.infrastructure.security;

import java.time.Duration;

/**
 * Auth Service'in token sözleşmesindeki (docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md §3) bu servisin kullandığı sabitler.
 * Değerlerin sahibi Auth'tur; bu servis onlara uyar, değiştirmez.
 */
public final class TokenClaims {

	/** JWT başlığı {@code typ}: kullanıcı access token'ı. */
	public static final String ACCESS_TOKEN_TYPE = "at+jwt";
	/** Auth'un servisler arası (iç uç) token türü; kullanıcı token'ıyla karıştırılmaz (D4 sözleşmesi). Yalnız {@code /internal/**}. */
	public static final String SERVICE_TOKEN_TYPE = "svc+jwt";
	/** Servis token'ının {@code sub} değeri {@code svc:<çağıran>} biçimindedir. */
	public static final String SERVICE_SUBJECT_PREFIX = "svc:";
	/** Servis token'ı en fazla bu kadar yaşar (D4 sözleşmesi: exp - iat ≤ 60 sn). */
	public static final Duration SERVICE_TOKEN_MAX_LIFETIME = Duration.ofSeconds(60);
	/** Olay girişi ({@code POST /internal/events}) kapsamı; Auth çağıran→hedef çifti başına verir. */
	public static final String EVENT_INGEST_SCOPE = "events:ingest";
	/** Servis token'ında yetki kapsamı. */
	public static final String SCOPE = "scope";
	/** Kullanıcı token'ında rol listesi, öneksiz ad ({@code PLAYER}, {@code MODERATOR}, {@code ADMIN} ...). */
	public static final String ROLES = "roles";
	/** Resmî duyuru oluşturabilen, öne çıkaran ve her içeriği kaldırabilen rol. */
	public static final String ADMIN_ROLE = "ADMIN";
	/** Yayımlanmış içeriği yayından kaldırabilen rol (resmî duyuru hariç). */
	public static final String MODERATOR_ROLE = "MODERATOR";
	/** İçerik Üreticisi rolü (CONT-17): yüksek içerik kotaları. */
	public static final String CONTENT_CREATOR_ROLE = "CONTENT_CREATOR";

	private TokenClaims() {
	}
}
