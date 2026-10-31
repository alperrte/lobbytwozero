package com.ltz.performance.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.StringWriter;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.tools.ToolProvider;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Auth üretim kaynaklarını değiştirmeden, kopyalamadan ve çalışma zamanı bağımlılığı eklemeden geçici test
 * dizinine derler. Uygulama/ENV/DB başlatılmaz; gerçek issuer ve JWKS controller çalıştırılır.
 * Bu bir kaynak SÖZLEŞME testidir: dağıtılmış Auth Service ile uçtan uca entegrasyon değildir.
 */
public final class ProductionAuthSupport implements AutoCloseable {
    private final URLClassLoader loader;
    private final Object signingKey;
    private final Object issuer;
    private final Object serviceIssuer;
    private final Object deviceIssuer;
    private final RSAKey key;

    public ProductionAuthSupport(Path output) throws Exception {
        Path services = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (services != null && !java.nio.file.Files.isDirectory(services.resolve("auth-service/src/main/java"))) {
            services = services.getParent();
        }
        assertNotNull(services, "Auth kaynakları repository checkout içinde bulunmalı");
        List<String> sources = new ArrayList<>();
        for (String name : List.of("domain/entity/Account", "domain/entity/AuthSession", "domain/entity/Role",
                "domain/enums/AccountStatus", "domain/enums/RoleCode", "domain/enums/AuthMethod",
                "domain/enums/SessionRevokeReason", "infrastructure/config/JwtProperties",
                "infrastructure/security/JwtSigningKey", "infrastructure/security/TokenClaims",
                "infrastructure/security/AccessTokenIssuer", "infrastructure/security/ServiceTokenIssuer",
                "infrastructure/security/DeviceTokenIssuer", "api/controller/JwksController",
                "api/controller/AuthApiPaths")) {
            sources.add(services.resolve("auth-service/src/main/java/com/ltz/auth/" + name + ".java").toString());
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Gerçek kaynak sözleşme testi JDK 25 gerektirir");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        try (var files = compiler.getStandardFileManager(null, Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8)) {
            StringWriter diagnostics = new StringWriter();
            boolean success = compiler.getTask(diagnostics, files, null,
                    List.of("--release", "25", "-encoding", "UTF-8", "-classpath", classpath, "-processorpath", classpath,
                            "-processor", "lombok.launch.AnnotationProcessorHider$AnnotationProcessor", "-d", output.toString()),
                    null, files.getJavaFileObjectsFromStrings(sources)).call();
            assertTrue(success, () -> "Üretim kaynakları derlenemedi: " + diagnostics);
        }
        loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, getClass().getClassLoader());
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        String encoded = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        signingKey = type("infrastructure.security.JwtSigningKey").getMethod("fromBase64Pkcs8", String.class).invoke(null, encoded);
        key = (RSAKey) signingKey.getClass().getMethod("jwk").invoke(signingKey);
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        Object properties = type("infrastructure.config.JwtProperties")
                .getConstructor(String.class, String.class, Duration.class, String.class)
                .newInstance("ltz-auth", "ltz-api", Duration.ofSeconds(60), encoded);
        issuer = type("infrastructure.security.AccessTokenIssuer").getConstructors()[0].newInstance(encoder, signingKey, properties);
        serviceIssuer = type("infrastructure.security.ServiceTokenIssuer").getConstructors()[0]
                .newInstance(encoder, signingKey, properties, java.time.Clock.systemUTC());
        deviceIssuer = type("infrastructure.security.DeviceTokenIssuer").getConstructors()[0]
                .newInstance(encoder, signingKey, properties, java.time.Clock.systemUTC());
    }

    public RSAKey key() { return key; }

    /** Gerçek Auth cihaz token'ı: bugün YALNIZ {@code aud=hardware-service} üretir (REQ-01-A). */
    public String deviceToken(UUID deviceId, UUID accountId) throws Exception {
        return (String) deviceIssuer.getClass().getMethod("issue", UUID.class, UUID.class)
                .invoke(deviceIssuer, deviceId, accountId);
    }

    /** Gerçek Auth D4 servis token'ı; {@code audience} hedef servis adıdır (ör. performance-service). */
    public String serviceToken(String audience, String scope, UUID accountId) throws Exception {
        return (String) serviceIssuer.getClass().getMethod("issue", String.class, String.class, UUID.class)
                .invoke(serviceIssuer, audience, scope, accountId);
    }

    public String jwks() throws Exception {
        Object controller = type("api.controller.JwksController").getConstructors()[0].newInstance(signingKey);
        Object result = controller.getClass().getMethod("jwks").invoke(controller);
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(result);
    }

    public String token(UUID accountId, String... roles) throws Exception {
        Instant now = Instant.now();
        Object initial = role(roles[0]);
        Class<?> accountType = type("domain.entity.Account");
        Object account = accountType.getMethod("registerVerifiedExternal", String.class, String.class, initial.getClass(), Instant.class)
                .invoke(null, "isolated@example.invalid", "isolated_test", initial, now);
        set(account, "id", accountId);
        for (int i = 1; i < roles.length; i++) {
            accountType.getMethod("grantRole", initial.getClass(), Instant.class).invoke(account, role(roles[i]), now);
        }
        Class<?> sessionType = type("domain.entity.AuthSession");
        Object method = type("domain.enums.AuthMethod").getField("PWD").get(null);
        long version = (long) accountType.getMethod("getTokenVersion").invoke(account);
        Object session = sessionType.getMethod("open", UUID.class, long.class, List.class, Instant.class, Instant.class)
                .invoke(null, accountId, version, List.of(method), now, now.plusSeconds(3600));
        set(session, "id", UUID.randomUUID());
        Object issued = issuer.getClass().getMethod("issue", accountType, sessionType, Instant.class)
                .invoke(issuer, account, session, now);
        return (String) issued.getClass().getMethod("value").invoke(issued);
    }

    private Object role(String code) throws Exception {
        var ctor = type("domain.entity.Role").getDeclaredConstructor();
        ctor.setAccessible(true);
        Object role = ctor.newInstance();
        set(role, "id", UUID.randomUUID());
        set(role, "code", type("domain.enums.RoleCode").getField(code).get(null));
        return role;
    }

    private Class<?> type(String name) throws ClassNotFoundException { return loader.loadClass("com.ltz.auth." + name); }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Override public void close() throws Exception { loader.close(); }
}
