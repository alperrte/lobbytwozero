package com.ltz.review.infrastructure.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceTokenDecoderLifecycleTest {
    private static final String AUDIENCE = "review-service";
    private static RSAKey key;

    @BeforeAll static void createEphemeralKey() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("lifecycle-test-key").generate();
    }

    @Test void jwksRequestsReuseClientAndPreserveCacheAndTimeouts() throws Exception {
        var config = new ServiceTokenDecoderConfiguration();
        try (var server = new JwksServer()) {
            try {
                assertNull(ownedClient(config));
                var firstDecoder = config.serviceJwtDecoder("ltz-auth", server.url("/jwks"), "", AUDIENCE);
                try (HttpClient client = ownedClient(config)) {
                    assertNotNull(client);
                    assertEquals(Duration.ofMillis(750), client.connectTimeout().orElseThrow());
                    assertEquals(HttpClient.Redirect.NEVER, client.followRedirects());
                    RestTemplate template = ReflectionTestUtils.invokeMethod(config, "jwksClient");
                    assertNotNull(template);
                    var factory = assertInstanceOf(JdkClientHttpRequestFactory.class, template.getRequestFactory());
                    assertSame(client, ReflectionTestUtils.getField(factory, "httpClient"));
                    assertEquals(Duration.ofMillis(750), ReflectionTestUtils.getField(factory, "readTimeout"));
                    assertEquals(0, server.requests.get());
                    assertEquals("svc:auth-service", firstDecoder.decode(token()).getSubject());
                    assertEquals("svc:auth-service", firstDecoder.decode(token()).getSubject());
                    assertEquals(1, server.requests.get());
                    var secondDecoder = config.serviceJwtDecoder("ltz-auth", server.url("/jwks"), "", AUDIENCE);
                    assertSame(client, ownedClient(config));
                    assertEquals("svc:auth-service", secondDecoder.decode(token()).getSubject());
                    assertEquals(2, server.requests.get());
                }
            } finally {
                config.closeJwksClient();
            }
        }
    }

    @Test void springContextClosesOwnedClientAndRepeatedCloseIsSafe() throws Exception {
        try (var server = new JwksServer(); var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("jwks-lifecycle", Map.of(
                    "ltz.security.jwt.issuer", "ltz-auth",
                    "ltz.security.jwt.jwk-set-uri", server.url("/jwks"),
                    "ltz.security.jwt.public-key", "")));
            context.register(ServiceTokenDecoderConfiguration.class);
            context.refresh();
            var config = context.getBean(ServiceTokenDecoderConfiguration.class);
            try (HttpClient client = ownedClient(config)) {
                assertNotNull(client);
                assertFalse(client.isTerminated());
                assertEquals("svc:auth-service", context.getBean("serviceJwtDecoder", JwtDecoder.class).decode(token()).getSubject());
                context.close();
                assertTrue(client.isTerminated());
                assertNull(ownedClient(config));
                assertDoesNotThrow(config::closeJwksClient);
                assertDoesNotThrow(context::close);
            }
        }
    }

    @Test void publicKeyModeValidatesSignatureWithoutCreatingHttpClient() throws Exception {
        var config = new ServiceTokenDecoderConfiguration();
        try {
            String pem = "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getEncoder().encodeToString(key.toRSAPublicKey().getEncoded()) + "\n-----END PUBLIC KEY-----";
            var decoder = config.serviceJwtDecoder("ltz-auth", "", pem, AUDIENCE);
            assertEquals("svc:auth-service", decoder.decode(token()).getSubject());
            assertNull(ownedClient(config));
        } finally {
            config.closeJwksClient();
        }
        assertNull(ownedClient(config));
        assertDoesNotThrow(config::closeJwksClient);
    }

    @Test void missingKeysFailClosedWithoutCreatingHttpClient() {
        var config = new ServiceTokenDecoderConfiguration();
        try {
            var decoder = config.serviceJwtDecoder("ltz-auth", "", "", AUDIENCE);
            assertThrows(JwtException.class, () -> decoder.decode("invalid"));
            assertNull(ownedClient(config));
        } finally {
            config.closeJwksClient();
        }
        assertNull(ownedClient(config));
        assertDoesNotThrow(config::closeJwksClient);
    }

    @Test void concurrentDecoderCreationUsesOneOwnedClient() throws Exception {
        var config = new ServiceTokenDecoderConfiguration();
        try {
            try (var workers = Executors.newFixedThreadPool(4)) {
                List<Future<HttpClient>> results = new ArrayList<>();
                for (int i = 0; i < 12; i++) results.add(workers.submit(() -> {
                    config.serviceJwtDecoder("ltz-auth", "http://127.0.0.1:1/jwks", "", AUDIENCE);
                    return ownedClient(config);
                }));
                try (HttpClient client = results.getFirst().get(5, TimeUnit.SECONDS)) {
                    assertNotNull(client);
                    for (var result : results) assertSame(client, result.get(5, TimeUnit.SECONDS));
                }
            }
        } finally {
            config.closeJwksClient();
        }
        assertNull(ownedClient(config));
    }

    @Test void redirectsAreRejectedWithoutFetchingTarget() throws Exception {
        var config = new ServiceTokenDecoderConfiguration();
        try (var server = new JwksServer()) {
            server.server.createContext("/redirect", exchange -> {
                try (exchange) {
                    exchange.getResponseHeaders().add("Location", server.url("/jwks"));
                    exchange.sendResponseHeaders(302, -1);
                }
            });
            try {
                var decoder = config.serviceJwtDecoder("ltz-auth", server.url("/redirect"), "", AUDIENCE);
                String signed = token();
                assertThrows(JwtException.class, () -> decoder.decode(signed));
                assertEquals(0, server.requests.get());
            } finally {
                config.closeJwksClient();
            }
        }
    }

    @Test void stalledJwksResponseTimesOutAndCanBeCleanedUp() throws Exception {
        var config = new ServiceTokenDecoderConfiguration();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var server = new JwksServer(); var worker = Executors.newSingleThreadExecutor()) {
            server.server.createContext("/slow", exchange -> {
                try (exchange) {
                    entered.countDown();
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            try {
                var decoder = config.serviceJwtDecoder("ltz-auth", server.url("/slow"), "", AUDIENCE);
                String signed = token();
                var result = worker.submit(() -> decoder.decode(signed));
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var error = assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
                assertInstanceOf(JwtException.class, error.getCause());
            } finally {
                release.countDown();
                config.closeJwksClient();
            }
        }
    }

    private static HttpClient ownedClient(ServiceTokenDecoderConfiguration config) {
        return (HttpClient) ReflectionTestUtils.getField(config, "jwksHttpClient");
    }

    private static String token() throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer("ltz-auth").audience(AUDIENCE).subject("svc:auth-service")
                .jwtID(UUID.randomUUID().toString()).issueTime(Date.from(now)).notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(30))).claim("scope", "account:erase")
                .claim("account_id", UUID.randomUUID().toString()).build();
        var signed = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("svc+jwt"))
                .keyID(key.getKeyID()).build(), claims);
        signed.sign(new RSASSASigner(key));
        return signed.serialize();
    }

    private static final class JwksServer implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger requests = new AtomicInteger();

        private JwksServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/jwks", exchange -> {
                requests.incrementAndGet();
                try (exchange) {
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (var output = exchange.getResponseBody()) { output.write(body); }
                }
            });
            server.start();
        }

        private String url(String path) { return "http://127.0.0.1:" + server.getAddress().getPort() + path; }
        @Override public void close() { server.stop(0); }
    }
}
