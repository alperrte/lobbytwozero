package com.ltz.game.infrastructure.client;

import com.ltz.game.infrastructure.client.FakeServer.Reply;
import com.ltz.game.infrastructure.config.GameRatingConfiguration;
import com.ltz.game.infrastructure.config.GameRatingProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.io.StringWriter;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Auth'un GERÇEK {@code ServiceClientTokenIssuer}'ı ve GERÇEK {@code application.properties} kaydı ile Game istemcilerinin
 * sözleşmesi (devir notu §3.5). Auth kaynakları kopyalanmaz: yalnız geçici test dizinine derlenir; Auth, ENV, DB veya
 * canlı servis başlatılmaz. Review'ın alıcı doğrulaması (typ/iss/aud/sub/scope/≤60 sn) burada token claim'leri üzerinden
 * denetlenir; Review'ın kendi güvenlik zinciri Review'ın testlerinde (InternalRatingSummaryTest) doğrulanır.
 */
class RealAuthServiceClientTokenTest {
    private static final String CLIENT = "game-service";
    private static final String TARGET = "review-service";
    private static final String SCOPE = "rating-summaries:read";
    private static final String ISSUER_KEY = "ltz.auth.service-clients.clients." + CLIENT;

    @TempDir Path output;

    private static Path authService() {
        Path services = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (services != null && !Files.isDirectory(services.resolve("auth-service/src/main/java"))) services = services.getParent();
        assertNotNull(services, "Auth kaynakları checkout içinde bulunmalı");
        return services.resolve("auth-service");
    }

    private static Properties properties(Path file) throws Exception {
        var result = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            result.load(reader);
        }
        return result;
    }

    @Test void authRegistersTheGameToReviewPairAndTheSameSecretVariableNameGameReads() throws Exception {
        Properties auth = properties(authService().resolve("src/main/resources/application.properties"));
        Properties game = properties(Path.of("src/main/resources/application.properties"));
        assertTrue(scopes(auth).contains(SCOPE), "Auth'ta game-service → review-service puan okuma kapsamı");
        // Aynı ENV adı iki tarafta da kullanılmalı; aksi halde Auth ve Game farklı değişkenleri okur ve token alınamaz.
        assertTrue(auth.getProperty(ISSUER_KEY + ".secret").contains("${AUTH_GAME_SERVICE_CLIENT_SECRET"));
        assertTrue(game.getProperty("ltz.game.rating.client-secret").contains("${AUTH_GAME_SERVICE_CLIENT_SECRET"));
    }

    @Test void gameClientsWorkWithATokenMintedByAuthsRealIssuerAndTheTokenSatisfiesReviewsRequirements() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072); // Auth en az 3072 bit ister
        KeyPair keys = generator.generateKeyPair();
        Properties auth = properties(authService().resolve("src/main/resources/application.properties"));
        // Kapsam, Auth'un gerçek kaydından okunur (sabit yazılmaz).
        Set<String> registered = scopes(auth);
        String token = mint(keys, registered);

        try (FakeServer authServer = new FakeServer(); FakeServer review = new FakeServer()) {
            authServer.enqueue(Reply.json(200, "{\"accessToken\":\"" + token + "\",\"tokenType\":\"Bearer\",\"expiresIn\":60,\"scope\":\""
                    + String.join(" ", registered) + "\"}"));
            review.enqueue(Reply.json(200, "{\"items\":[{\"gameId\":\"00000000-0000-4000-8000-000000000001\",\"averageRating\":8.5,\"ratingCount\":3}],\"nextAfter\":null}"));
            var props = new GameRatingProperties(review.baseUrl(), authServer.baseUrl(), "/internal/v1/service-tokens", CLIENT,
                    "test-only-secret-value", TARGET, Duration.ofSeconds(1), Duration.ofSeconds(2), 500,
                    new GameRatingProperties.Sync(true, Duration.ofMinutes(5), Duration.ofSeconds(30), Duration.ofMinutes(2)));
            var tokens = new AuthServiceTokenClient(GameRatingConfiguration.restClient(RestClient.builder(), authServer.baseUrl(),
                    props.connectTimeout(), props.readTimeout()), Clock.systemUTC(), props);
            var client = new ReviewRatingClient(GameRatingConfiguration.restClient(RestClient.builder(), review.baseUrl(),
                    props.connectTimeout(), props.readTimeout()), tokens, TARGET, Duration.ZERO, "/internal/v1/games/rating-summaries");

            var page = client.fetchPage(null, 500);
            assertEquals(1, page.items().size());
            assertTrue(authServer.received().getFirst().body().contains("\"audience\":\"" + TARGET + "\""));

            // Review'a giden Authorization başlığındaki token, Auth'un gerçek üreticisinin tokenıdır ve Review'ın aradığı
            // alıcı sözleşmesini sağlar.
            String sent = review.received().getFirst().authorization();
            assertEquals("Bearer " + token, sent);
            SignedJWT jwt = SignedJWT.parse(sent.substring("Bearer ".length()));
            assertTrue(jwt.verify(new RSASSAVerifier((RSAPublicKey) keys.getPublic())), "RS256 imzası Auth anahtarıyla doğrulanır");
            assertEquals("RS256", jwt.getHeader().getAlgorithm().getName());
            assertEquals("svc+jwt", jwt.getHeader().getType().toString());
            assertNotNull(jwt.getHeader().getKeyID());
            var claims = jwt.getJWTClaimsSet();
            assertEquals("ltz-auth", claims.getIssuer());
            assertEquals(List.of(TARGET), claims.getAudience(), "tek aud=review-service");
            assertEquals("svc:" + CLIENT, claims.getSubject(), "tam sub=svc:game-service");
            assertEquals(registered, Set.copyOf(Arrays.asList(claims.getStringClaim("scope").split(" "))));
            assertFalse(claims.getJWTID() == null || claims.getJWTID().isBlank());
            long lifetime = claims.getExpirationTime().toInstant().getEpochSecond() - claims.getIssueTime().toInstant().getEpochSecond();
            assertTrue(lifetime > 0 && lifetime <= 60, "ömür ≤ 60 sn: " + lifetime);
            assertNotNull(claims.getNotBeforeTime());
        }
    }

    private static Set<String> scopes(Properties auth) {
        return Set.copyOf(Arrays.asList(auth.getProperty(ISSUER_KEY + ".targets." + TARGET).split(",")));
    }

    /** Auth'un gerçek ServiceClientTokenIssuer'ını geçici dizine derleyip game-service → review-service token'ı üretir. */
    private String mint(KeyPair keys, Set<String> scopes) throws Exception {
        Path auth = authService();
        List<String> sources = new ArrayList<>();
        for (String name : List.of("infrastructure/config/JwtProperties", "infrastructure/security/TokenClaims",
                "infrastructure/security/JwtSigningKey", "infrastructure/security/ServiceClientTokenIssuer"))
            sources.add(auth.resolve("src/main/java/com/ltz/auth/" + name + ".java").toString());
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK 25 gereklidir");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        try (var files = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
            var diagnostics = new StringWriter();
            boolean success = compiler.getTask(diagnostics, files, null,
                    List.of("--release", "25", "-encoding", "UTF-8", "-proc:none", "-classpath", classpath, "-d", output.toString()),
                    null, files.getJavaFileObjectsFromStrings(sources)).call();
            assertTrue(success, () -> "Auth test derlemesi başarısız: " + diagnostics);
        }
        try (var loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> signingType = loader.loadClass("com.ltz.auth.infrastructure.security.JwtSigningKey");
            String encoded = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());
            Object signingKey = signingType.getMethod("fromBase64Pkcs8", String.class).invoke(null, encoded);
            RSAKey jwk = (RSAKey) signingType.getMethod("jwk").invoke(signingKey);
            var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
            var propsType = loader.loadClass("com.ltz.auth.infrastructure.config.JwtProperties");
            var props = propsType.getConstructor(String.class, String.class, Duration.class, String.class)
                    .newInstance("ltz-auth", "ltz-api", Duration.ofMinutes(5), encoded);
            var issuerType = loader.loadClass("com.ltz.auth.infrastructure.security.ServiceClientTokenIssuer");
            var issuer = issuerType.getConstructors()[0].newInstance(encoder, signingKey, props, Clock.systemUTC());
            return (String) issuerType.getMethod("issue", String.class, String.class, Set.class).invoke(issuer, CLIENT, TARGET, scopes);
        }
    }
}
