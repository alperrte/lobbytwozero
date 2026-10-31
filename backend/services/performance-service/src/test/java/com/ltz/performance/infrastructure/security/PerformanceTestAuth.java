package com.ltz.performance.infrastructure.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Test JVM'i başına bir kez: gerçek Auth üretim kaynaklarından JWKS + yerel bir Auth SAPMASI.
 * Sapma yalnız {@code GET /api/v1/auth/devices} yanıtını taklit eder (gerçek Auth'un belgelenmiş cihaz listesi
 * şekli); bu bir mock'tur ve gerçek Auth entegrasyonu sayılmaz. Hesap başına yanıt kodu/gövdesi ayarlanabilir.
 */
public final class PerformanceTestAuth {
    public static final Path COMPILED;
    public static final ProductionAuthSupport AUTH;
    private static final HttpServer SERVER;
    private static final Map<UUID, List<String[]>> DEVICES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> FORCED_STATUS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> FORCED_BODY = new ConcurrentHashMap<>();
    private static volatile int jwksStatus = 200;

    static {
        try {
            COMPILED = Files.createTempDirectory("performance-auth-contract-");
            AUTH = new ProductionAuthSupport(COMPILED);
            byte[] jwks = AUTH.jwks().getBytes(StandardCharsets.UTF_8);
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            SERVER.createContext("/api/v1/auth/.well-known/jwks.json", exchange -> {
                if (jwksStatus != 200) {
                    exchange.sendResponseHeaders(jwksStatus, -1);
                } else {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, jwks.length);
                    exchange.getResponseBody().write(jwks);
                }
                exchange.close();
            });
            SERVER.createContext("/api/v1/auth/devices", PerformanceTestAuth::devices);
            SERVER.start();
            Runtime.getRuntime().addShutdownHook(new Thread(PerformanceTestAuth::release));
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private PerformanceTestAuth() { }

    public static String serviceUrl() { return "http://127.0.0.1:" + SERVER.getAddress().getPort(); }

    public static void setJwksStatus(int status) { jwksStatus = status; }

    /** Auth'un bu hesap için kullanılabilir saydığı cihazlar. */
    public static void authDevices(UUID account, UUID deviceId, String label) {
        DEVICES.computeIfAbsent(account, _ -> new ArrayList<>()).add(new String[]{deviceId.toString(), label});
    }

    public static void forceDeviceListStatus(UUID account, int status, String body) {
        FORCED_STATUS.put(account, status);
        if (body != null) FORCED_BODY.put(account, body);
    }

    public static void reset(UUID account) {
        DEVICES.remove(account);
        FORCED_STATUS.remove(account);
        FORCED_BODY.remove(account);
    }

    /** Performance hedefli cihaz token'ı (REQ-01-A sonrası gerçek Auth'un üreteceği sözleşme biçimi; yerel imza). */
    public static String performanceDeviceToken(UUID deviceId, UUID account) throws Exception {
        return performanceDeviceToken(deviceId, account, _ -> { });
    }

    public static String performanceDeviceToken(UUID deviceId, UUID account, Consumer<Map<String, Object>> mutate)
            throws Exception {
        Instant now = Instant.now();
        var claims = new HashMap<String, Object>();
        claims.put("iss", "ltz-auth");
        claims.put("sub", deviceId.toString());
        claims.put("aud", List.of("performance-service"));
        claims.put("account_id", account.toString());
        claims.put("scope", "performance:sync:write");
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("iat", now.getEpochSecond());
        claims.put("nbf", now.getEpochSecond());
        claims.put("exp", now.plusSeconds(60).getEpochSecond());
        mutate.accept(claims);
        return sign(AUTH.key(), new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(AUTH.key().getKeyID())
                .type(new JOSEObjectType("dev+jwt")).build(), claims);
    }

    public static String sign(RSAKey key, JWSHeader header, Map<String, Object> claims) throws Exception {
        var normalized = new HashMap<>(claims);
        normalized.replaceAll((_, value) -> value instanceof Date date ? date.toInstant().getEpochSecond() : value);
        var jwt = new SignedJWT(header, JWTClaimsSet.parse(normalized));
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static void devices(HttpExchange exchange) throws IOException {
        try {
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            UUID account = account(header);
            int status = account == null ? 401 : FORCED_STATUS.getOrDefault(account, 200);
            byte[] body = new byte[0];
            if (status == 200) {
                String forced = FORCED_BODY.get(account);
                body = (forced != null ? forced : list(account)).getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, status == 200 ? body.length : -1);
            if (status == 200) exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private static UUID account(String header) {
        try {
            if (header == null || !header.startsWith("Bearer ")) return null;
            return UUID.fromString(SignedJWT.parse(header.substring(7)).getJWTClaimsSet().getSubject());
        } catch (Exception _) {
            return null;
        }
    }

    private static String list(UUID account) {
        var entries = new ArrayList<String>();
        for (String[] device : DEVICES.getOrDefault(account, List.of())) {
            entries.add("{\"deviceId\":\"%s\",\"label\":\"%s\",\"createdAt\":\"2026-01-01T00:00:00Z\","
                    .formatted(device[0], device[1]) + "\"expiresAt\":\"2026-12-31T00:00:00Z\",\"lastUsedAt\":null}");
        }
        return "[" + String.join(",", entries) + "]";
    }

    private static void release() {
        SERVER.stop(0);
        try {
            AUTH.close();
            try (var files = Files.walk(COMPILED)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        } catch (Exception exception) {
            // Geçici dizin temizliği başarısızsa test sonucu etkilenmez; yalnız uyarı yazılır.
            System.getLogger(PerformanceTestAuth.class.getName())
                    .log(System.Logger.Level.WARNING, "Geçici Auth sözleşme dizini silinemedi", exception);
        }
    }
}
