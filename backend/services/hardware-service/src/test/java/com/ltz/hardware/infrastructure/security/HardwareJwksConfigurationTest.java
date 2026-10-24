package com.ltz.hardware.infrastructure.security;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.BadJwtException;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

class HardwareJwksConfigurationTest {
    static final Duration HTTP_TIMEOUT=Duration.ofSeconds(1);
    static final RSAKey FIRST;
    static final RSAKey SECOND;
    static {
        try { FIRST=new RSAKeyGenerator(3072).keyID("first-test-key").generate();
            SECOND=new RSAKeyGenerator(3072).keyID("second-test-key").generate(); }
        catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
    HttpServer server;
    final AtomicInteger requests=new AtomicInteger();
    final AtomicInteger responseCode=new AtomicInteger(200);
    final AtomicReference<String> responseBody=new AtomicReference<>(new JWKSet(FIRST.toPublicJWK()).toString());
    final AtomicBoolean slowBody=new AtomicBoolean();
    final java.util.concurrent.CountDownLatch releaseBody=new java.util.concurrent.CountDownLatch(1);
    HardwareAuthProperties properties;
    JWKSource<SecurityContext> source;

    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/auth/.well-known/jwks.json",exchange -> {
            requests.incrementAndGet();
            byte[] body=responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Location","http://127.0.0.1:1/untrusted-redirect");
            exchange.sendResponseHeaders(responseCode.get(),body.length);
            try (var output=exchange.getResponseBody()) {
                if (slowBody.get()) {
                    output.write(body,0,1); output.flush();
                    try { releaseBody.await(3,java.util.concurrent.TimeUnit.SECONDS); }
                    catch (InterruptedException _) { Thread.currentThread().interrupt(); }
                    output.write(body,1,body.length-1);
                } else output.write(body);
            }
            exchange.close();
        });
        server.start();
        properties=new HardwareAuthProperties(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),
                "ltz-auth","ltz-api",Duration.ofMillis(250),Duration.ofMillis(500));
        source=new HardwareJwksConfiguration().hardwareJwkSource(properties);
    }
    @AfterEach void close() throws Exception { releaseBody.countDown(); ((AutoCloseable)source).close(); server.stop(0); }

    @Test void readTimeoutAlsoBoundsAResponseWhoseHeadersArriveButBodyStalls() throws Exception {
        slowBody.set(true);
        var decoder=new HardwareJwtConfiguration().userDecoder(properties,source,Clock.systemUTC());
        String token=HardwareJwtTestTokens.signed(FIRST,UUID.randomUUID(),false,c -> {});
        try (var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var result=executor.submit(() -> decoder.decode(token));
            try {
                assertThatThrownBy(() -> result.get(1200,java.util.concurrent.TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class).hasCauseInstanceOf(BadJwtException.class);
            } finally { releaseBody.countDown(); }
        }
    }

    @Test void knownKeysAreCachedAndRotatedKeyIsFetchedFromConfiguredOrigin() throws Exception {
        var decoder=new HardwareJwtConfiguration().userDecoder(properties,source,Clock.systemUTC());
        String first=HardwareJwtTestTokens.signed(FIRST,UUID.randomUUID(),false,c -> {});
        decoder.decode(first); decoder.decode(first);
        assertThat(requests.get()).isEqualTo(1);
        responseBody.set(new JWKSet(List.of(FIRST.toPublicJWK(),SECOND.toPublicJWK())).toString());
        String rotated=HardwareJwtTestTokens.signed(SECOND,UUID.randomUUID(),false,c -> {});
        await().atMost(Duration.ofSeconds(3)).ignoreException(BadJwtException.class)
                .untilAsserted(() -> assertThat(decoder.decode(rotated).getSubject()).isNotBlank());
        assertThat(requests.get()).isEqualTo(2);
        server.stop(0);
        assertThat(decoder.decode(first).getSubject()).isNotBlank();
    }

    @ParameterizedTest @ValueSource(strings={"unavailable","redirect","oversized","malformed","unknownKey"})
    void untrustedOrUnavailableJwksFailsClosedWithoutLeakingTransportDetails(String scenario) throws Exception {
        switch(scenario) {
            case "unavailable" -> responseCode.set(503);
            case "redirect" -> responseCode.set(302);
            case "oversized" -> responseBody.set("x".repeat(65537));
            case "malformed" -> responseBody.set("invalid-json");
            default -> responseBody.set(new JWKSet(SECOND.toPublicJWK()).toString());
        }
        var decoder=new HardwareJwtConfiguration().userDecoder(properties,source,Clock.systemUTC());
        String token=HardwareJwtTestTokens.signed(FIRST,UUID.randomUUID(),false,c -> {});
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class)
                .hasMessage("Token validation failed");
        assertThat(requests.get()).isBetween(1,2);
    }

    @ParameterizedTest @ValueSource(strings={"ftp://auth-service","https://u:p@auth-service","https://auth-service/path",
            "https://auth-service?x=1","https://auth-service#fragment","http:invalid"})
    void invalidAuthOriginsAreRejected(String origin) {
        URI uri=URI.create(origin);
        assertThatThrownBy(() -> new HardwareAuthProperties(uri,"ltz-auth","ltz-api",HTTP_TIMEOUT,HTTP_TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void missingConfigAndUnboundedTimeoutsAreRejected() {
        URI uri=URI.create("https://auth-service/");
        assertThatThrownBy(() -> new HardwareAuthProperties(null,"issuer","audience",HTTP_TIMEOUT,HTTP_TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);
        for(String value:List.of("", " ")) {
            assertThatThrownBy(() -> new HardwareAuthProperties(uri,value,"audience",HTTP_TIMEOUT,HTTP_TIMEOUT))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new HardwareAuthProperties(uri,"issuer",value,HTTP_TIMEOUT,HTTP_TIMEOUT))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for(Duration timeout:Arrays.asList(null,Duration.ZERO,Duration.ofMillis(-1),Duration.ofSeconds(31)))
            assertThatThrownBy(() -> new HardwareAuthProperties(uri,"issuer","audience",timeout,HTTP_TIMEOUT))
                    .isInstanceOf(IllegalArgumentException.class);
        assertThat(new HardwareAuthProperties(uri,"issuer","audience",HTTP_TIMEOUT,HTTP_TIMEOUT).jwksUri().getPath())
                .isEqualTo("/api/v1/auth/.well-known/jwks.json");
    }
}
