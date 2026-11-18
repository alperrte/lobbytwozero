package com.ltz.event;

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
 * Auth Service yerine geçen yerel bir JWKS sunucusu ve Auth'un kullanıcı token biçimini
 * ({@code docs-auth/servis-sozlesmeleri.md}) taklit eden imzalı token üreticileri. Anahtar her test çalıştırmasında
 * üretilir; repoda gerçek anahtar ya da token yoktur. Token'lar gerçekten RS256 ile imzalanır, böylece doğrulama
 * (imza, iss, aud, typ, süre) sahte yardımcılarla atlanmaz.
 */
public final class AuthTestSupport {

	public static final String ISSUER = "ltz-auth";
	public static final String USER_AUDIENCE = "ltz-api";
	public static final String KEY_ID = "event-test-key";

	private static final JOSEObjectType ACCESS_TYPE = new JOSEObjectType("at+jwt");
	private static final JOSEObjectType SERVICE_TYPE = new JOSEObjectType("svc+jwt");
	private static final int RSA_BITS = 2048;

	/** Bu çalıştırmanın imza anahtarı. */
	public static final RSAKey SIGNING_KEY = generateKey(KEY_ID);

	private static final HttpServer JWKS_SERVER = startJwksServer(SIGNING_KEY.toPublicJWK());

	private AuthTestSupport() {
	}

	/** Yerel JWKS adresi ({@code ltz.event.auth.jwks-uri} olarak verilir). */
	public static String jwksUri() {
		return "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/jwks.json";
	}

	public static RSAKey generateKey(String keyId) {
		try {
			return new RSAKeyGenerator(RSA_BITS).keyID(keyId).generate();
		} catch (JOSEException exception) {
			throw new IllegalStateException(exception);
		}
	}

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

	private static HttpServer startJwksServer(JWK publicKey) {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			byte[] jwks = new JWKSet(new ArrayList<JWK>(List.of(publicKey))).toString()
					.getBytes(StandardCharsets.UTF_8);
			server.createContext("/jwks.json", exchange -> {
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
