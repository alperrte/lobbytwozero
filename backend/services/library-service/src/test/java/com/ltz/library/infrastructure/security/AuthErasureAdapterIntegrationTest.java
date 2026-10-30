package com.ltz.library.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D4 hesap silme sözleşmesi, Auth'un GERÇEK HTTP adaptörü (HttpAccountDataEraser + ServiceTokenIssuer, kaynaktan derlenir)
 * ile Library'nin GERÇEK HTTP sunucusu (RANDOM_PORT, gerçek güvenlik zinciri, JWKS, PostgreSQL) arasında sınanır.
 * Auth kaynakları değiştirilmez/kopyalanmaz; yalnız geçici dizine derlenir. Gerçek Auth servisi veya gizli anahtar kullanılmaz:
 * imza anahtarı bu testin kendi geçici anahtarıdır. Gerçek Auth ↔ Library canlı silme akışı (14 günlük bekleme) ayrıca doğrulanır.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "logging.level.root=WARN")
class AuthErasureAdapterIntegrationTest {
    private static final String TEST_DATABASE = "library-erasure-adapter-test";
    private static final String TEST_USER = "ltz_library_erasure_test";
    private static final PostgreSQLContainer POSTGRES = Boolean.getBoolean("ltz.test.external-postgres") ? null : start();
    private static final KeyPair KEYS = keys();
    private static final HttpServer JWKS_SERVER = jwksServer();
    private static final String AUTH_CLASSES = "com.ltz.auth.";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("ltz.security.jwt.jwk-set-uri", () -> "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/jwks");
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
    }

    @AfterAll static void stop() { JWKS_SERVER.stop(0); }

    @Test void authHttpAdapterErasesOnlyBoundAccountAndIsIdempotentOverRealHttp(@TempDir Path output) throws Exception {
        UUID erased = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        seed(erased, 2);
        seed(kept, 3);
        try (Adapter adapter = new Adapter(output, "library", URI.create("http://127.0.0.1:" + port))) {
            adapter.erase("library", erased);
            assertEquals(0, count(erased));
            assertEquals(3, count(kept), "Başka kullanıcının verisi korunmalı");
            adapter.erase("library", erased);
            adapter.erase("library", UUID.randomUUID());
            assertEquals(3, count(kept), "Tekrarlı ve veri olmayan hesap silmeleri idempotent olmalı");
        }
    }

    @Test void tokenForAnotherAudienceIsNotConfirmedAndLeavesDataUntouched(@TempDir Path output) throws Exception {
        UUID owner = UUID.randomUUID();
        seed(owner, 1);
        // Auth, "game" hedefi için aud=game-service token'ı üretir; adres Library'ye yönlendirilmiş olsa bile Library kabul etmemeli.
        try (Adapter adapter = new Adapter(output, "game", URI.create("http://127.0.0.1:" + port))) {
            var failure = assertThrows(IllegalStateException.class, () -> adapter.erase("game", owner));
            assertTrue(failure.getMessage().contains("SERVICE_ERASURE_HTTP_401"), failure.getMessage());
            assertEquals(1, count(owner));
            assertFalse(failure.getMessage().contains("Bearer") || failure.getMessage().contains("eyJ"));
        }
    }

    private void seed(UUID user, int games) {
        for (int i = 0; i < games; i++)
            jdbc.update("INSERT INTO library_entries (id, user_id, game_id) VALUES (?, ?, ?)", UUID.randomUUID(), user, UUID.randomUUID());
    }

    private int count(UUID user) {
        return jdbc.queryForObject("SELECT count(*) FROM library_entries WHERE user_id = ?", Integer.class, user);
    }

    /** Auth'un gerçek adaptörünü yansıma ile çalıştırır; hata kodu IllegalStateException(code) olarak yüzeye çıkar. */
    private static final class Adapter implements AutoCloseable {
        private final URLClassLoader loader;
        private final Object eraser;

        Adapter(Path output, String service, URI baseUrl) throws Exception {
            Path services = Path.of(System.getProperty("user.dir")).toAbsolutePath();
            while (services != null && !Files.isDirectory(services.resolve("auth-service/src/main/java"))) services = services.getParent();
            assertNotNull(services, "Auth kaynakları repository checkout içinde bulunmalı");
            List<String> sources = new ArrayList<>();
            for (String name : List.of("infrastructure/config/JwtProperties", "infrastructure/config/DeletionProperties",
                    "infrastructure/security/TokenClaims", "infrastructure/security/JwtSigningKey", "infrastructure/security/ServiceTokenIssuer",
                    "application/port/AccountDataEraserPort", "application/port/AccountErasureException", "infrastructure/client/HttpAccountDataEraser"))
                sources.add(services.resolve("auth-service/src/main/java/com/ltz/auth/" + name + ".java").toString());
            var compiler = ToolProvider.getSystemJavaCompiler();
            assertNotNull(compiler, "Gerçek kaynak sözleşme testi JDK 25 gerektirir");
            String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            try (var files = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
                var diagnostics = new StringWriter();
                boolean success = compiler.getTask(diagnostics, files, null,
                        List.of("--release", "25", "-encoding", "UTF-8", "-classpath", classpath, "-processorpath", classpath,
                                "-processor", "lombok.launch.AnnotationProcessorHider$AnnotationProcessor", "-d", output.toString()),
                        null, files.getJavaFileObjectsFromStrings(sources)).call();
                assertTrue(success, () -> "Auth kaynakları derlenemedi: " + diagnostics);
            }
            loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, getClass().getClassLoader());
            Class<?> signingType = loader.loadClass(AUTH_CLASSES + "infrastructure.security.JwtSigningKey");
            String encoded = Base64.getEncoder().encodeToString(KEYS.getPrivate().getEncoded());
            Object signingKey = signingType.getMethod("fromBase64Pkcs8", String.class).invoke(null, encoded);
            var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet((RSAKey) signingType.getMethod("jwk").invoke(signingKey))));
            Object jwtProperties = loader.loadClass(AUTH_CLASSES + "infrastructure.config.JwtProperties")
                    .getConstructor(String.class, String.class, Duration.class, String.class)
                    .newInstance("ltz-auth", "ltz-api", Duration.ofMinutes(5), encoded);
            Object issuer = loader.loadClass(AUTH_CLASSES + "infrastructure.security.ServiceTokenIssuer").getConstructors()[0]
                    .newInstance(encoder, signingKey, jwtProperties, Clock.systemUTC());
            Class<?> deletionType = loader.loadClass(AUTH_CLASSES + "infrastructure.config.DeletionProperties");
            Object deletion = deletionType.getConstructors()[0].newInstance(Set.of(service), Map.of(service, baseUrl),
                    Duration.ofSeconds(5), Duration.ofMinutes(30), Duration.ofSeconds(60), Duration.ofMinutes(10),
                    URI.create("http://localhost:5001/account/delete"), false, Duration.ofSeconds(5), 10, Duration.ofSeconds(30),
                    Duration.ofSeconds(1), Duration.ofSeconds(10), 5, Duration.ZERO);
            eraser = loader.loadClass(AUTH_CLASSES + "infrastructure.client.HttpAccountDataEraser").getConstructors()[0]
                    .newInstance(deletion, issuer);
        }

        void erase(String service, UUID account) throws Exception {
            try {
                eraser.getClass().getMethod("erase", String.class, UUID.class, UUID.class).invoke(eraser, service, UUID.randomUUID(), account);
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                String code = (String) cause.getClass().getMethod("code").invoke(cause);
                throw new IllegalStateException(code);
            }
        }

        @Override public void close() throws Exception {
            var close = eraser.getClass().getDeclaredMethod("close"); // paket-özel @PreDestroy: HTTP istemcisini kapatır
            close.setAccessible(true);
            close.invoke(eraser);
            loader.close();
        }
    }

    private static PostgreSQLContainer start() {
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:17-alpine").withDatabaseName(TEST_DATABASE).withUsername(TEST_USER);
        container.start();
        return container;
    }

    private static KeyPair keys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            return generator.generateKeyPair();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private static HttpServer jwksServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var jwk = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) KEYS.getPublic()).keyIDFromThumbprint().build();
            byte[] body = new JWKSet(jwk).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
}
