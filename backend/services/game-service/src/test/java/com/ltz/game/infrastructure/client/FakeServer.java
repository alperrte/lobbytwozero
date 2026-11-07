package com.ltz.game.infrastructure.client;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Yalnız yerel döngü adresine bağlanan, sıralı yanıt veren sahte Auth/Review sunucusu (gerçek ağ yok). */
final class FakeServer implements AutoCloseable {

    record Reply(int status, String body, long delayMillis, String location) {
        static Reply json(int status, String body) {
            return new Reply(status, body, 0, null);
        }

        static Reply slow(long delayMillis) {
            return new Reply(200, "{}", delayMillis, null);
        }

        static Reply redirect(String location) {
            return new Reply(302, "", 0, location);
        }
    }

    record Received(String method, String pathAndQuery, String authorization, String body, String apiKey, String userAgent) { }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Queue<Reply> replies = new LinkedList<>();
    private final List<Received> received = Collections.synchronizedList(new ArrayList<>());
    private final java.util.concurrent.atomic.AtomicInteger abandoned = new java.util.concurrent.atomic.AtomicInteger();
    private volatile Reply fallback = Reply.json(500, "{}");
    private volatile String retryAfter;

    FakeServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String query = exchange.getRequestURI().getRawQuery();
            received.add(new Received(exchange.getRequestMethod(),
                    exchange.getRequestURI().getRawPath() + (query == null ? "" : "?" + query),
                    exchange.getRequestHeaders().getFirst("Authorization"), body,exchange.getRequestHeaders().getFirst("ITAD-API-Key"),
                    exchange.getRequestHeaders().getFirst("User-Agent")));
            Reply reply;
            synchronized (replies) {
                reply = replies.isEmpty() ? fallback : replies.poll();
            }
            if (reply.delayMillis() > 0) {
                try {
                    new java.util.concurrent.CountDownLatch(1).await(reply.delayMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            if(retryAfter!=null) exchange.getResponseHeaders().set("Retry-After",retryAfter);
            if (reply.location() != null) {
                exchange.getResponseHeaders().add("Location", reply.location());
            }
            try {
                exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
                if (bytes.length > 0) {
                    exchange.getResponseBody().write(bytes);
                }
            } catch (IOException _) {
                // İstemci zaman aşımına uğrayıp bağlantıyı kapattı; test bunu beklenen davranış olarak sınar.
                abandoned.incrementAndGet();
            } finally {
                exchange.close();
            }
        });
        // Gecikmeli yanıt sonraki istekleri bloklamasın: her istek kendi iş parçacığında alınır ve kaydedilir.
        server.setExecutor(executor);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Sıradaki yanıtlar bitince verilecek yanıt (varsayılan 500). */
    FakeServer fallback(Reply reply) {
        fallback = reply;
        return this;
    }
    FakeServer retryAfter(String value) { retryAfter=value;return this; }

    FakeServer enqueue(Reply... next) {
        synchronized (replies) {
            replies.addAll(List.of(next));
        }
        return this;
    }

    /** İstemcinin yanıtı beklemeden bağlantıyı kapattığı istek sayısı (zaman aşımı kanıtı). */
    int abandoned() {
        return abandoned.get();
    }

    List<Received> received() {
        synchronized (received) {
            return List.copyOf(received);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
