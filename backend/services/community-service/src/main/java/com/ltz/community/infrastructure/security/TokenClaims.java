package com.ltz.community.infrastructure.security;

import java.time.Duration;

/**
 * Auth Service'in token sözleşmesindeki (docs-auth/servis-sozlesmeleri.md §1-§3) bu servisin kullandığı sabitler.
 * Değerlerin sahibi Auth'tur; bu servis onlara uyar, değiştirmez.
 */
public final class TokenClaims {

	/** JWT başlığı {@code typ}: kullanıcı access token'ı. */
	public static final String ACCESS_TOKEN_TYPE = "at+jwt";
	/** Auth'un servisler arası (iç uç) token türü; kullanıcı token'ıyla karıştırılmaz (D4 sözleşmesi). */
	public static final String SERVICE_TOKEN_TYPE = "svc+jwt";
	/** Servis token'ının {@code sub} değeri {@code svc:<çağıran>} biçimindedir. */
	public static final String SERVICE_SUBJECT_PREFIX = "svc:";
	/** Hesap silme çağrısının tek geçerli çağıranı. */
	public static final String AUTH_SERVICE_SUBJECT = "svc:auth-service";
	/** Servis token'ı en fazla bu kadar yaşar (D4 sözleşmesi: exp - iat ≤ 60 sn). */
	public static final Duration SERVICE_TOKEN_MAX_LIFETIME = Duration.ofSeconds(60);
	/** Hesap verisi silme eylemi için servis token'ı kapsamı (Auth tanımlar). */
	public static final String ACCOUNT_ERASE_SCOPE = "account:erase";
	/** Silme token'ının bağlandığı hesap (Auth tanımlar); yoldaki {@code {accountId}} ile eşit olmalıdır. */
	public static final String ACCOUNT_ID = "account_id";
	/**
	 * Olay girişi ({@code POST /internal/events}) için ÖNERİLEN kapsam adı. Auth bu kapsamı henüz üretmiyor; adı ve
	 * üretimi Auth'tan talep edildi (docs-community/integration/auth/entegrasyon-v1/auth-service-talepleri/auth-requests-01.md).
	 */
	public static final String EVENT_INGEST_SCOPE = "events:ingest";
	/** Kullanıcı token'ında rol listesi, öneksiz ad ({@code PLAYER}, {@code MODERATOR}, {@code ADMIN} ...). */
	public static final String ROLES = "roles";
	/** Servis token'ında yetki kapsamı. */
	public static final String SCOPE = "scope";

	private TokenClaims() {
	}
}
