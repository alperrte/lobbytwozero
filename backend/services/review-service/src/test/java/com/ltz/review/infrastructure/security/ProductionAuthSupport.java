package com.ltz.review.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.tools.ToolProvider;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Auth'un GERÇEK üretim kaynaklarını (AccessTokenIssuer, ServiceTokenIssuer, JwksController, HttpAccountDataEraser)
 * değiştirmeden ve kopyalamadan yalnız geçici test dizinine derler ve çalıştırır. Gerçek Auth servisi, ENV, gizli anahtar
 * veya veritabanı kullanılmaz: imza anahtarı testin kendi geçici anahtarıdır. Bu, canlı Auth uçtan uca doğrulaması değildir.
 */
public final class ProductionAuthSupport implements AutoCloseable {
    private static final String AUTH = "com.ltz.auth.";
    private final URLClassLoader loader;
    private final Object signingKey;
    private final Object userIssuer;
    private final Object serviceIssuer;
    private final Class<?> deletionType;
    private final Class<?> eraserType;

    public ProductionAuthSupport(Path output, KeyPair keys) throws Exception {
        Path services = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (services != null && !Files.isDirectory(services.resolve("auth-service/src/main/java"))) services = services.getParent();
        assertNotNull(services, "Auth kaynakları repository checkout içinde bulunmalı");
        List<String> sources = new ArrayList<>();
        for (String name : List.of("domain/entity/Account", "domain/entity/AuthSession", "domain/entity/Role",
                "domain/enums/AccountStatus", "domain/enums/RoleCode", "domain/enums/AuthMethod", "domain/enums/SessionRevokeReason",
                "infrastructure/config/JwtProperties", "infrastructure/config/DeletionProperties", "infrastructure/security/JwtSigningKey",
                "infrastructure/security/TokenClaims", "infrastructure/security/AccessTokenIssuer", "infrastructure/security/ServiceTokenIssuer",
                "api/controller/JwksController", "api/controller/AuthApiPaths", "application/port/AccountDataEraserPort",
                "application/port/AccountErasureException", "infrastructure/client/HttpAccountDataEraser"))
            sources.add(services.resolve("auth-service/src/main/java/com/ltz/auth/" + name + ".java").toString());
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Gerçek kaynak sözleşme testi JDK 25 gerektirir");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        try (var files = compiler.getStandardFileManager(null, Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8)) {
            var diagnostics = new StringWriter();
            boolean success = compiler.getTask(diagnostics, files, null,
                    List.of("--release", "25", "-encoding", "UTF-8", "-classpath", classpath, "-processorpath", classpath,
                            "-processor", "lombok.launch.AnnotationProcessorHider$AnnotationProcessor", "-d", output.toString()),
                    null, files.getJavaFileObjectsFromStrings(sources)).call();
            assertTrue(success, () -> "Auth kaynakları derlenemedi: " + diagnostics);
        }
        loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, getClass().getClassLoader());
        String encoded = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());
        signingKey = type("infrastructure.security.JwtSigningKey").getMethod("fromBase64Pkcs8", String.class).invoke(null, encoded);
        RSAKey jwk = (RSAKey) signingKey.getClass().getMethod("jwk").invoke(signingKey);
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        Object properties = type("infrastructure.config.JwtProperties").getConstructor(String.class, String.class, Duration.class, String.class)
                .newInstance("ltz-auth", "ltz-api", Duration.ofMinutes(5), encoded);
        userIssuer = type("infrastructure.security.AccessTokenIssuer").getConstructors()[0].newInstance(encoder, signingKey, properties);
        serviceIssuer = type("infrastructure.security.ServiceTokenIssuer").getConstructors()[0]
                .newInstance(encoder, signingKey, properties, Clock.systemUTC());
        deletionType = type("infrastructure.config.DeletionProperties");
        eraserType = type("infrastructure.client.HttpAccountDataEraser");
    }

    /** Auth JwksController'ın yayınladığı JWK kümesi (JSON). */
    public String jwks() throws Exception {
        Object controller = type("api.controller.JwksController").getConstructors()[0].newInstance(signingKey);
        Object result = controller.getClass().getMethod("jwks").invoke(controller);
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(result);
    }

    /** Auth AccessTokenIssuer'ın ürettiği kullanıcı token'ı (typ=at+jwt); roller Auth RoleCode adlarıdır. */
    public String userToken(UUID accountId, String... roles) throws Exception {
        Instant now = Instant.now();
        Class<?> accountType = type("domain.entity.Account");
        Object initial = role(roles[0]);
        Object account = accountType.getMethod("registerVerifiedExternal", String.class, String.class, initial.getClass(), Instant.class)
                .invoke(null, "isolated@example.invalid", "isolated_test", initial, now);
        set(account, "id", accountId);
        for (int i = 1; i < roles.length; i++) accountType.getMethod("grantRole", initial.getClass(), Instant.class).invoke(account, role(roles[i]), now);
        Class<?> sessionType = type("domain.entity.AuthSession");
        Object method = type("domain.enums.AuthMethod").getField("PWD").get(null);
        long version = (long) accountType.getMethod("getTokenVersion").invoke(account);
        Object session = sessionType.getMethod("open", UUID.class, long.class, List.class, Instant.class, Instant.class)
                .invoke(null, accountId, version, List.of(method), now, now.plusSeconds(3600));
        set(session, "id", UUID.randomUUID());
        Object issued = userIssuer.getClass().getMethod("issue", accountType, sessionType, Instant.class).invoke(userIssuer, account, session, now);
        return (String) issued.getClass().getMethod("value").invoke(issued);
    }

    /** Auth ServiceTokenIssuer'ın ürettiği hesaba bağlı servis token'ı (typ=svc+jwt, sub=svc:auth-service). */
    public String serviceToken(String audience, String scope, UUID accountId) throws Exception {
        return (String) serviceIssuer.getClass().getMethod("issue", String.class, String.class, UUID.class)
                .invoke(serviceIssuer, audience, scope, accountId);
    }

    /**
     * Auth'un gerçek HttpAccountDataEraser'ını çalıştırır. {@code service} hedef adıdır (token aud={@code service}-service),
     * {@code baseUrl} çağrının gideceği adrestir. Onay sayılmayan sonuçta Auth hata kodu IllegalStateException olur.
     */
    public void erase(String service, URI baseUrl, UUID account) throws Exception {
        Object deletion = deletionType.getConstructors()[0].newInstance(Set.of(service), Map.of(service, baseUrl),
                Duration.ofSeconds(5), Duration.ofMinutes(30), Duration.ofSeconds(60), Duration.ofMinutes(10),
                URI.create("http://localhost:5001/account/delete"), false, Duration.ofSeconds(5), 10, Duration.ofSeconds(30),
                Duration.ofSeconds(1), Duration.ofSeconds(10), 5, Duration.ZERO);
        Object eraser = eraserType.getConstructors()[0].newInstance(deletion, serviceIssuer);
        try {
            eraserType.getMethod("erase", String.class, UUID.class, UUID.class).invoke(eraser, service, UUID.randomUUID(), account);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            throw new IllegalStateException((String) cause.getClass().getMethod("code").invoke(cause));
        } finally {
            var close = eraserType.getDeclaredMethod("close"); // paket-özel @PreDestroy: HTTP istemcisini kapatır
            close.setAccessible(true);
            close.invoke(eraser);
        }
    }

    private Object role(String code) throws Exception {
        var constructor = type("domain.entity.Role").getDeclaredConstructor();
        constructor.setAccessible(true);
        Object role = constructor.newInstance();
        set(role, "id", UUID.randomUUID());
        set(role, "code", type("domain.enums.RoleCode").getField(code).get(null));
        return role;
    }

    private Class<?> type(String name) throws ClassNotFoundException { return loader.loadClass(AUTH + name); }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Override public void close() throws Exception { loader.close(); }
}
