package com.ltz.performance.infrastructure.client;

import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Game (GAME-15) ve Hardware (HW-17) servislerinin YEREL SAPMALARI: docs/api.md'deki sözleşme biçimini taklit eden
 * mock sunucudur. Gerçek Game/Hardware entegrasyonu DEĞİLDİR; yalnız Performance istemcilerinin sözleşme
 * davranışını (200/404/hata/bozuk gövde/zaman aşımı) sınamak içindir.
 */
public final class PerformancePeerDoubles {
    private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final HttpServer SERVER;
    private static final Map<UUID, String> GAMES = new ConcurrentHashMap<>();
    private static final Set<String> PROFILES = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger GAME_CALLS = new AtomicInteger();
    private static final AtomicInteger HARDWARE_CALLS = new AtomicInteger();
    private static final AtomicReference<String> LAST_HARDWARE_AUTHORIZATION = new AtomicReference<>();
    private static volatile int gameStatus = 200;
    private static volatile String gameBody;
    private static volatile int hardwareStatus = 200;
    private static volatile String hardwareBody;
    private static volatile long hardwareDelayMillis;

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            SERVER.createContext("/api/v1/game/games/batch", PerformancePeerDoubles::games);
            SERVER.createContext("/api/v1/hardware/profiles/batch", PerformancePeerDoubles::hardware);
            // Gecikmeli bir Hardware yanıtı sonraki testlerin Game çağrısını bekletmesin.
            SERVER.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "peer-double");
                thread.setDaemon(true);
                return thread;
            }));
            SERVER.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> SERVER.stop(0)));
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private PerformancePeerDoubles() { }

    public static String origin() { return "http://127.0.0.1:" + SERVER.getAddress().getPort(); }

    public static void registerGame(UUID gameId, String title) { GAMES.put(gameId, title); }

    public static void registerProfile(UUID account, UUID profileId) { PROFILES.add(account + ":" + profileId); }

    public static void forceGame(int status, String body) {
        gameStatus = status;
        gameBody = body;
    }

    public static void forceHardware(int status, String body, long delayMillis) {
        hardwareStatus = status;
        hardwareBody = body;
        hardwareDelayMillis = delayMillis;
    }

    public static void resetBehaviour() {
        forceGame(200, null);
        forceHardware(200, null, 0);
        GAME_CALLS.set(0);
        HARDWARE_CALLS.set(0);
        LAST_HARDWARE_AUTHORIZATION.set(null);
    }

    public static int gameCalls() { return GAME_CALLS.get(); }

    public static int hardwareCalls() { return HARDWARE_CALLS.get(); }

    public static String lastHardwareAuthorization() { return LAST_HARDWARE_AUTHORIZATION.get(); }

    private static void games(HttpExchange exchange) throws IOException {
        try {
            GAME_CALLS.incrementAndGet();
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (gameStatus != 200) {
                send(exchange, gameStatus, null);
                return;
            }
            if (gameBody != null) {
                send(exchange, 200, gameBody);
                return;
            }
            var items = new StringBuilder();
            var unavailable = new StringBuilder();
            var matcher = UUID_TEXT.matcher(request);
            while (matcher.find()) {
                var id = UUID.fromString(matcher.group());
                var title = GAMES.get(id);
                if (title != null) {
                    if (!items.isEmpty()) items.append(',');
                    items.append("{\"gameId\":\"%s\",\"title\":\"%s\"}".formatted(id, title));
                } else {
                    if (!unavailable.isEmpty()) unavailable.append(',');
                    unavailable.append('"').append(id).append('"');
                }
            }
            send(exchange, 200, "{\"items\":[%s],\"unavailable\":[%s]}".formatted(items, unavailable));
        } finally {
            exchange.close();
        }
    }

    private static void hardware(HttpExchange exchange) throws IOException {
        try {
            HARDWARE_CALLS.incrementAndGet();
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            LAST_HARDWARE_AUTHORIZATION.set(authorization);
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            sleep(hardwareDelayMillis);
            if (hardwareStatus != 200) {
                send(exchange, hardwareStatus, null);
                return;
            }
            if (hardwareBody != null) {
                send(exchange, 200, hardwareBody);
                return;
            }
            var matcher = UUID_TEXT.matcher(request);
            UUID profile = matcher.find() ? UUID.fromString(matcher.group()) : null;
            UUID account = account(authorization);
            if (profile == null || account == null || !PROFILES.contains(account + ":" + profile)) {
                send(exchange, 404, null);
                return;
            }
            send(exchange, 200, "{\"profiles\":[{\"profileId\":\"%s\"}]}".formatted(profile));
        } finally {
            exchange.close();
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static UUID account(String header) {
        try {
            if (header == null || !header.startsWith("Bearer ")) return null;
            return UUID.fromString(SignedJWT.parse(header.substring(7)).getJWTClaimsSet().getSubject());
        } catch (Exception _) {
            return null;
        }
    }

    private static void sleep(long millis) {
        if (millis <= 0) return;
        try {
            // Gecikmeli yanıtı simüle eder; hiç sayılmayan sayaç yalnız zaman aşımına kadar bekler.
            new CountDownLatch(1).await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }
}
