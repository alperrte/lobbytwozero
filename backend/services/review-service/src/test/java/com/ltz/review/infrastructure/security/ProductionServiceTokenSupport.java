package com.ltz.review.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.StringWriter;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.tools.ToolProvider;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Auth'un dört gerçek kaynak dosyasını yalnız geçici test dizinine derler; ENV/DB başlatmaz. */
public final class ProductionServiceTokenSupport {
    private ProductionServiceTokenSupport() {}

    public static String issue(Path output, KeyPair keys, String audience, UUID account) throws Exception {
        Path services = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (services != null && !Files.isDirectory(services.resolve("auth-service/src/main/java")))
            services = services.getParent();
        assertNotNull(services, "Auth kaynakları checkout içinde bulunmalı");
        List<String> sources = new ArrayList<>();
        for (String name : List.of("infrastructure/config/JwtProperties", "infrastructure/security/TokenClaims",
                "infrastructure/security/JwtSigningKey", "infrastructure/security/ServiceTokenIssuer"))
            sources.add(services.resolve("auth-service/src/main/java/com/ltz/auth/" + name + ".java").toString());
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK 25 gereklidir");
        var classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        try (var files = compiler.getStandardFileManager(null, Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8)) {
            var diagnostics = new StringWriter();
            boolean success = compiler.getTask(diagnostics, files, null,
                    List.of("--release", "25", "-encoding", "UTF-8", "-proc:none", "-classpath", classpath, "-d", output.toString()),
                    null, files.getJavaFileObjectsFromStrings(sources)).call();
            assertTrue(success, () -> "Auth test derlemesi başarısız: " + diagnostics);
        }
        try (var loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, ProductionServiceTokenSupport.class.getClassLoader())) {
            Class<?> signingType = loader.loadClass("com.ltz.auth.infrastructure.security.JwtSigningKey");
            String encoded = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());
            Object signingKey = signingType.getMethod("fromBase64Pkcs8", String.class).invoke(null, encoded);
            RSAKey jwk = (RSAKey) signingType.getMethod("jwk").invoke(signingKey);
            var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
            var propsType = loader.loadClass("com.ltz.auth.infrastructure.config.JwtProperties");
            var props = propsType.getConstructor(String.class, String.class, Duration.class, String.class)
                    .newInstance("ltz-auth", "ltz-api", Duration.ofMinutes(5), encoded);
            var issuerType = loader.loadClass("com.ltz.auth.infrastructure.security.ServiceTokenIssuer");
            var issuer = issuerType.getConstructors()[0].newInstance(encoder, signingKey, props, Clock.systemUTC());
            return (String) issuerType.getMethod("issue", String.class, String.class, UUID.class)
                    .invoke(issuer, audience, "account:erase", account);
        }
    }
}
