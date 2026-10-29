package com.ltz.game;

import com.jayway.jsonpath.JsonPath;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.application.service.CatalogImportService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Korumalı uç testlerinin ortak altyapısı. Anahtar çifti ve token'lar YALNIZ test içindir: üretim koduna veya
 * yapılandırma dosyalarına girmez, bu sınıf dışında kullanılmaz. Gerçek Auth Service entegrasyonu doğrulanmış sayılmaz;
 * burada yalnız game-service'in JWT doğrulama, rol çözümleme ve yetkilendirme davranışı sınanır.
 * Testler geri alınan transaction içinde çalışır (append-only denetim tabloları ve paylaşılan DB kirlenmez).
 */
@SpringBootTest @Import(AuthenticatedApiTest.Fakes.class) @Transactional
public abstract class AuthenticatedApiTest extends PostgresTestSupport {
    protected static final String ISSUER = "ltz-auth";
    protected static final String AUDIENCE = "ltz-api";
    protected static final KeyPair KEYS = generateKeys();
    protected static final KeyPair OTHER_KEYS = generateKeys();
    protected static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    protected static final UUID MODERATOR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    protected static final UUID DEVELOPER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    protected static final UUID OTHER_DEVELOPER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry properties) {
        String pem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(KEYS.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
        properties.add("ltz.security.jwt.public-key", () -> pem);
        properties.add("ltz.security.jwt.jwk-set-uri", () -> "");
        properties.add("ltz.security.jwt.issuer", () -> ISSUER);
        properties.add("ltz.security.jwt.audience", () -> AUDIENCE);
        // Toplu uç istek sınırı testleri belirleyici olsun diye sürekli dolum yok denecek kadar yavaştır (üretim varsayılanı 20/sn).
        properties.add("ltz.rate-limit.batch.refill-per-second", () -> "0.001");
    }

    @TestConfiguration
    public static class Fakes {
        @Bean @Primary FakeProvider authApiProvider() { return new FakeProvider(); }
    }

    /** Katalog/detay sağlayıcısı: senkronizasyonun manuel düzeltmeleri ezmediğini sınamak için. */
    public static class FakeProvider implements GameProvider {
        public final Queue<Supplier<CatalogPage>> pages = new ConcurrentLinkedQueue<>();
        public final Queue<Supplier<Details>> details = new ConcurrentLinkedQueue<>();
        @Override public String provider() { return "STEAM"; }
        @Override public Set<Capability> capabilities() {
            return Set.of(Capability.CATALOG, Capability.INCREMENTAL_CATALOG, Capability.DETAILS, Capability.ITEM_TYPE);
        }
        @Override public CatalogPage catalog(CatalogQuery query) { return Objects.requireNonNull(pages.poll()).get(); }
        @Override public Details details(String id) { return Objects.requireNonNull(details.poll()).get(); }
        public void page(long id, String title, boolean more) {
            pages.add(() -> new CatalogPage(List.of(new CatalogItem("" + id, title, Instant.EPOCH, "game")), id, more));
        }
        public void detail(String description, LocalDate release) {
            details.add(() -> new Details(description, release, Set.of("Provider Studio"), Set.of("Provider Publisher"), "game"));
        }
    }

    @Autowired protected WebApplicationContext context;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected FakeProvider provider;
    protected MockMvc mvc;

    @BeforeEach
    void authenticatedApiSetup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        provider.pages.clear();
        provider.details.clear();
        // Append-only denetim kayıtları (FK RESTRICT) yalnız bu geçici test veritabanında TRUNCATE ile temizlenir.
        jdbc.execute("truncate game_admin_audits");
        jdbc.update("delete from game_external_refs");
        jdbc.update("delete from games");
        jdbc.update("update game_provider_sync_state set cursor=0,sync_status='IDLE',run_mode=null,query_since=null,run_started_at=null,"
                + "last_successful_at=null,processed_count=0,last_error_code=null,failure_count=0,next_attempt_at=null");
    }

    // ------------------------------------------------------------------ token yardımcıları (yalnız test)

    protected static KeyPair generateKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    protected static JWTClaimsSet.Builder claims(Object subject, Object roles) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder().issuer(ISSUER).audience(AUDIENCE)
                .issueTime(Date.from(now)).notBeforeTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString()).claim("sid", UUID.randomUUID().toString())
                .claim("ver", 0L).claim("amr", List.of("pwd"));
        if (subject != null) builder.subject(subject.toString());
        if (roles != null) builder.claim("roles", roles);
        return builder;
    }

    protected static String sign(JWTClaimsSet.Builder claims, PrivateKey key) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .type(new com.nimbusds.jose.JOSEObjectType("at+jwt")).keyID("game-test-key").build(), claims.build());
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    protected static String token(UUID user, String... roles) { return sign(claims(user, List.of(roles)), KEYS.getPrivate()); }
    protected static String admin() { return token(ADMIN_ID, "ADMIN"); }
    protected static String moderator() { return token(MODERATOR_ID, "MODERATOR"); }
    protected static String developer() { return token(DEVELOPER_ID, "GAME_DEVELOPER"); }
    protected static String otherDeveloper() { return token(OTHER_DEVELOPER_ID, "GAME_DEVELOPER"); }

    // ------------------------------------------------------------------ istek yardımcıları

    protected ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    protected static String read(ResultActions result, String path) throws Exception {
        Object value = JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
        return String.valueOf(value);
    }

    protected UUID game(String title, String origin, String type, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,created_at,updated_at,origin,game_type,status,metadata_completeness) "
                + "values (?,?,?,now(),now(),?,?,?,'INDEX_ONLY')", id, title, CatalogImportService.normalize(title), origin, type, status);
        return id;
    }
}
