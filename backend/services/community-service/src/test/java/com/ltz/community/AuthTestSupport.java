package com.ltz.community;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

/**
 * Auth Service yerine geçen yerel bir JWKS sunucusu ve Auth'un token biçimini ({@code docs-auth/servis-sozlesmeleri.md})
 * taklit eden imzalı token üreticileri. Anahtar her test çalıştırmasında üretilir; repoda gerçek anahtar ya da token yoktur.
 * Token'lar gerçekten RS256 ile imzalanır, böylece doğrulama (imza, iss, aud, typ, süre) sahte yardımcılarla atlanmaz.
 */
public final class AuthTestSupport {

	public static final String ISSUER = "ltz-auth";
	public static final String USER_AUDIENCE = "ltz-api";
	public static final String SERVICE_AUDIENCE = "community-service";
	public static final String ERASE_SCOPE = "account:erase";
	public static final String INGEST_SCOPE = "events:ingest";
	public static final String AUTH_SUBJECT = "svc:auth-service";
	public static final String KEY_ID = "community-test-key";

	private static final JOSEObjectType ACCESS_TYPE = new JOSEObjectType("at+jwt");
	private static final JOSEObjectType SERVICE_TYPE = new JOSEObjectType("svc+jwt");
	private static final int RSA_BITS = 2048;

	/** Bu çalıştırmanın imza anahtarı. */
	public static final RSAKey SIGNING_KEY = generateKey(KEY_ID);

	private static final List<RSAKey> PUBLISHED_KEYS = new CopyOnWriteArrayList<>();
	private static final HttpServer JWKS_SERVER;

	static {
		PUBLISHED_KEYS.add(SIGNING_KEY.toPublicJWK());
		JWKS_SERVER = startJwksServer();
	}

	private AuthTestSupport() {
	}

	/** Yerel JWKS adresi ({@code ltz.community.auth.jwks-uri} olarak verilir). */
	public static String jwksUri() {
		return "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/jwks.json";
	}

	/** Auth'un anahtar dönüşümünü taklit eder: yeni açık anahtarı JWKS'e ekler. */
	public static void publishKey(RSAKey key) {
		PUBLISHED_KEYS.add(key.toPublicJWK());
	}

	public static RSAKey generateKey(String keyId) {
		try {
			return new RSAKeyGenerator(RSA_BITS).keyID(keyId).generate();
		} catch (JOSEException exception) {
			throw new IllegalStateException(exception);
		}
	}

	// ------------------------------------------------------------------ kullanıcı token'ı (at+jwt)

	public static String userToken(UUID userId, String... roles) {
		return userToken(userId, claims -> claims.claim("roles", List.of(roles)));
	}

	public static String userToken(UUID userId, UnaryOperator<JWTClaimsSet.Builder> change) {
		return sign(SIGNING_KEY, ACCESS_TYPE, change.apply(validUserClaims(userId)).build());
	}

	public static JWTClaimsSet.Builder validUserClaims(UUID userId) {
		Instant now = Instant.now();
		return new JWTClaimsSet.Builder()
				.issuer(ISSUER)
				.audience(USER_AUDIENCE)
				.subject(userId.toString())
				.jwtID(UUID.randomUUID().toString())
				.issueTime(Date.from(now))
				.notBeforeTime(Date.from(now))
				.expirationTime(Date.from(now.plusSeconds(300)))
				.claim("roles", List.of("PLAYER"));
	}

	// ------------------------------------------------------------------ servis token'ı (svc+jwt)

	/** Auth'un D4 sözleşmesindeki biçimde (hedef: community, çağıran ve kapsam verilir) geçerli servis token'ı. */
	public static String serviceToken(String subject, String scope, UnaryOperator<JWTClaimsSet.Builder> change) {
		return sign(SIGNING_KEY, SERVICE_TYPE, change.apply(validServiceClaims(subject, scope)).build());
	}

	/** Auth → community hesap silme token'ı (geçerli). */
	public static String eraseToken(UUID accountId) {
		return serviceToken(AUTH_SUBJECT, ERASE_SCOPE, claims -> claims.claim("account_id", accountId.toString()));
	}

	public static JWTClaimsSet.Builder validServiceClaims(String subject, String scope) {
		Instant now = Instant.now();
		return new JWTClaimsSet.Builder()
				.issuer(ISSUER)
				.audience(SERVICE_AUDIENCE)
				.subject(subject)
				.jwtID(UUID.randomUUID().toString())
				.issueTime(Date.from(now))
				.notBeforeTime(Date.from(now))
				.expirationTime(Date.from(now.plusSeconds(30)))
				.claim("scope", scope);
	}

	// ------------------------------------------------------------------ imzalama

	public static String sign(RSAKey key, JOSEObjectType type, JWTClaimsSet claims) {
		try {
			JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(type).build();
			SignedJWT jwt = new SignedJWT(header, claims);
			jwt.sign(new RSASSASigner(key));
			return jwt.serialize();
		} catch (JOSEException exception) {
			throw new IllegalStateException(exception);
		}
	}

	/** HS256 ile imzalı token (kabul edilmemelidir: yalnız RS256 geçerlidir). */
	public static String hs256Token(JWTClaimsSet claims, JOSEObjectType type) {
		try {
			byte[] secret = new byte[32];
			java.util.Arrays.fill(secret, (byte) 7);
			JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(KEY_ID).type(type).build();
			SignedJWT jwt = new SignedJWT(header, claims);
			jwt.sign(new MACSigner(secret));
			return jwt.serialize();
		} catch (JOSEException exception) {
			throw new IllegalStateException(exception);
		}
	}

	/** {@code alg=none} (imzasız) token. */
	public static String unsignedToken(JWTClaimsSet claims) {
		return new PlainJWT(claims).serialize();
	}

	public static JOSEObjectType accessType() {
		return ACCESS_TYPE;
	}

	public static JOSEObjectType serviceType() {
		return SERVICE_TYPE;
	}

	private static HttpServer startJwksServer() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			server.createContext("/jwks.json", exchange -> {
				byte[] jwks = new JWKSet(new ArrayList<JWK>(PUBLISHED_KEYS)).toString().getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, jwks.length);
				try (OutputStream body = exchange.getResponseBody()) {
					body.write(jwks);
				}
			});
			server.start();
			return server;
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
