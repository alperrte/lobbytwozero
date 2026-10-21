package com.ltz.auth;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.ltz.auth.api.controller.AuthApiPaths;
import com.ltz.auth.application.service.SessionService;
import com.ltz.auth.domain.enums.AuthMethod;
import com.ltz.auth.infrastructure.security.SecretCipher;
import com.ltz.auth.infrastructure.security.TotpCodes;

import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;

/**
 * Gerçek PostgreSQL 17 (Compose ile aynı imaj) üzerinde, Flyway migration'ları uygulanmış tam uygulama
 * bağlamı. Container tüm test sınıflarında paylaşılır; testler benzersiz e-posta/kullanıcı adı kullanır.
 * HMAC anahtarı her çalıştırmada rastgele üretilir; repoda veya çıktıda gerçek sır yoktur.
 */
@SpringBootTest
public abstract class IntegrationTestSupport {

    protected static final String VALID_PASSWORD = "correct-horse-battery-staple";

    protected static final String ACCESS_COOKIE = "__Host-ltz_access";
    protected static final String REFRESH_COOKIE = "__Secure-ltz_refresh";
    protected static final String CSRF_COOKIE = "__Host-XSRF-TOKEN";
    protected static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static final String TEST_HMAC_KEY = randomKey();
    private static final String TEST_TOTP_KEY = randomKey();
    private static final int TEST_RSA_BITS = 3072;
    /** Her test çalıştırmasında üretilen imza anahtarı; repoda veya çıktıda gerçek anahtar yoktur. */
    protected static final KeyPair TEST_JWT_KEYS = rsaKeyPair();

    static {
        POSTGRES.start();
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private SecretCipher secretCipher;

    @Autowired
    private TotpCodes totpCodes;

    @Autowired
    private SessionService sessionService;

    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("AUTH_OTP_HMAC_KEY", () -> TEST_HMAC_KEY);
        // Silme worker'ı testlerde elle tetiklenir (deterministik); hedef servis listesi kısaltılır.
        registry.add("ltz.auth.deletion.worker-enabled", () -> "false");
        // Temizlik de elle tetiklenir; arka plan çalıştırması testlerin verisini silmemeli.
        registry.add("ltz.auth.cleanup.enabled", () -> "false");
        // Eşzamanlılık testleri kapasite değil tekillik doğrular; yüklü makinede Argon2 slot beklemesi 503 üretmesin.
        registry.add("ltz.auth.password-hashing.acquire-timeout", () -> "PT10S");
        registry.add("ltz.auth.deletion.targets", () -> "user,mail");
        registry.add("AUTH_TOTP_ENCRYPTION_KEY", () -> TEST_TOTP_KEY);
        registry.add("AUTH_JWT_PRIVATE_KEY",
                () -> Base64.getEncoder().encodeToString(TEST_JWT_KEYS.getPrivate().getEncoded()));
    }

    /** Gerçek sunucudaki filtre sırası: istek kimliği, gövde sınırı, ardından Spring Security zinciri. */
    @BeforeEach
    void setUpMockMvc() {
        Filter requestId = context.getBean("requestIdFilter", FilterRegistrationBean.class).getFilter();
        Filter bodySizeLimit = context.getBean("requestBodySizeLimitFilter", FilterRegistrationBean.class).getFilter();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestId, bodySizeLimit)
                .apply(springSecurity())
                .build();
    }

    /** Kayıt API'si ile hesap açar ve e-posta doğrulanmış (ACTIVE) duruma getirir. */
    protected String registerActiveAccount(String email, String username) throws Exception {
        register(email, username, VALID_PASSWORD).andExpect(status().isAccepted());
        jdbc.update("update accounts set status = 'ACTIVE', email_verified_at = now() where email_normalized = lower(?)",
                email);
        return email;
    }

    protected void grantRole(UUID accountId, String roleCode) {
        jdbc.update("insert into roles (id, code) values (gen_random_uuid(), ?) on conflict (code) do nothing", roleCode);
        jdbc.update("insert into account_roles (account_id, role_id) select ?, id from roles where code = ?",
                accountId, roleCode);
    }

    /**
     * Uygulamanın gerçek CookieCsrfTokenRepository'si ile double-submit: aynı değer cookie ve başlıkta.
     * (spring-security-test'in csrf() yardımcısı depoyu tüm context için değiştirdiğinden kullanılmaz.)
     */
    protected static RequestPostProcessor xsrf() {
        String token = UUID.randomUUID().toString();
        return request -> {
            Cookie[] existing = request.getCookies() == null ? new Cookie[0] : request.getCookies();
            Cookie[] cookies = Arrays.copyOf(existing, existing.length + 1);
            cookies[existing.length] = new Cookie(CSRF_COOKIE, token);
            request.setCookies(cookies);
            request.addHeader(CSRF_HEADER, token);
            return request;
        };
    }

    /** Hesaba doğrudan ACTIVE bir TOTP kaydı ekler (2FA kurulum akışı bu testlerin konusu değildir). */
    protected void enableTotp(UUID accountId) {
        var secret = secretCipher.encrypt(totpCodes.newSecret(), accountId);
        jdbc.update("insert into totp_credentials (account_id, secret_ciphertext, secret_nonce, key_version, status, "
                + "created_at, activated_at) values (?, ?, ?, ?, 'ACTIVE', now(), now())", accountId,
                secret.ciphertext(), secret.nonce(), secret.keyVersion());
    }

    /** Hesabın etkin TOTP sırrından o anki geçerli kodu üretir. */
    protected String currentTotpCode(UUID accountId) {
        var row = jdbc.queryForMap(
                "select secret_ciphertext, secret_nonce, key_version from totp_credentials where account_id = ?", accountId);
        byte[] secret = secretCipher.decrypt(new SecretCipher.EncryptedSecret((byte[]) row.get("secret_ciphertext"),
                (byte[]) row.get("secret_nonce"), ((Number) row.get("key_version")).shortValue()), accountId);
        return totpCodes.codeAt(secret, totpCodes.stepAt(java.time.Instant.now()));
    }

    /** Gerçek token üreticisiyle verilmiş, belirtilen rolleri taşıyan geçerli oturumun Authorization başlığı. */
    protected String bearerFor(UUID accountId) {
        return "Bearer " + sessionService.start(accountId, List.of(AuthMethod.PWD)).accessToken();
    }

    protected ResultActions login(String identifier, String password) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.LOGIN).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"identifier":"%s","password":"%s"}
                        """.formatted(identifier, password)));
    }

    /** Başarılı girişin cookie'leri (access + refresh). */
    protected SessionCookiePair loginSession(String identifier) throws Exception {
        MvcResult result = login(identifier, VALID_PASSWORD).andExpect(status().isOk()).andReturn();
        return SessionCookiePair.from(result);
    }

    protected ResultActions refresh(Cookie refreshCookie) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.SESSION_REFRESH).cookie(refreshCookie).with(xsrf()));
    }

    public record SessionCookiePair(Cookie access, Cookie refresh) {

        public static SessionCookiePair from(MvcResult result) {
            return new SessionCookiePair(result.getResponse().getCookie(ACCESS_COOKIE),
                    result.getResponse().getCookie(REFRESH_COOKIE));
        }
    }

    private static KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(TEST_RSA_BITS);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    protected static String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    protected static String uniqueEmail() {
        return "player." + uniqueSuffix() + "@example.test";
    }

    protected static String uniqueUsername() {
        return "p_" + uniqueSuffix();
    }

    protected ResultActions postJson(String path, String json) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions register(String email, String username, String password) throws Exception {
        return postJson(AuthApiPaths.REGISTER, """
                {"email":"%s","username":"%s","password":"%s"}
                """.formatted(email, username, password));
    }

    /** Parola ile kayıt olan hesabın e-posta doğrulaması: kayıttaki parola kanıt olarak gönderilir. */
    protected ResultActions confirm(String email, String code) throws Exception {
        return confirmWithPassword(email, code, VALID_PASSWORD);
    }

    protected ResultActions confirmWithPassword(String email, String code, String password) throws Exception {
        return postJson(AuthApiPaths.EMAIL_VERIFICATION_CONFIRM, """
                {"email":"%s","code":"%s","password":"%s"}
                """.formatted(email, code, password));
    }

    protected ResultActions confirmWithProof(String email, String code, String proof) throws Exception {
        return postJson(AuthApiPaths.EMAIL_VERIFICATION_CONFIRM, """
                {"email":"%s","code":"%s","verificationProof":"%s"}
                """.formatted(email, code, proof));
    }

    protected ResultActions confirmWithoutProof(String email, String code) throws Exception {
        return postJson(AuthApiPaths.EMAIL_VERIFICATION_CONFIRM, """
                {"email":"%s","code":"%s"}
                """.formatted(email, code));
    }

    protected ResultActions resend(String email) throws Exception {
        return postJson(AuthApiPaths.EMAIL_VERIFICATION_RESEND, """
                {"email":"%s"}
                """.formatted(email));
    }

    protected UUID accountId(String email) {
        return jdbc.queryForObject("select id from accounts where email_normalized = lower(?)", UUID.class, email);
    }

    protected int accountCount(String email) {
        return jdbc.queryForObject("select count(*) from accounts where email_normalized = lower(?)",
                Integer.class, email);
    }

    protected String accountStatus(String email) {
        return jdbc.queryForObject("select status from accounts where email_normalized = lower(?)",
                String.class, email);
    }

    protected int securityEventCount(UUID accountId, String type) {
        return jdbc.queryForObject("select count(*) from security_events where account_id = ? and type = ?",
                Integer.class, accountId, type);
    }

    /** Bekleme/süre testleri için kod zamanlarını geriye kaydırır (uygulama saati gerçek kalır). */
    protected void shiftCodesBack(UUID accountId, String interval) {
        jdbc.update("update email_verification_codes set created_at = created_at - cast(? as interval), "
                + "expires_at = expires_at - cast(? as interval) where account_id = ?", interval, interval, accountId);
    }

    private static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
