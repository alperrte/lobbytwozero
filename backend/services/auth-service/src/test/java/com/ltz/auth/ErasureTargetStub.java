package com.ltz.auth;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Yalnız test: hedef servislerin iç silme ucunu ({@code DELETE /internal/v1/accounts/{id}}) taklit eden yerel HTTP
 * sunucusu (loopback, rastgele port). Gelen istekleri kaydeder; yanıt kodu teste göre ayarlanır. Varsayılan yanıt
 * {@code 503}: silme onaylanmadı. Yönlendirme yanıtlarında aynı sunucuya {@code Location} verir (izlenmediği kanıtlanır).
 */
public final class ErasureTargetStub implements AutoCloseable {

    public static final String REDIRECT_PATH = "/redirected";
    private static final int UNAVAILABLE = 503;
    private static final int NO_BODY = -1;

    /** Hedefin gördüğü istek: yöntem, ham yol ve Authorization başlığı. */
    public record Received(String method, String path, String authorization) {

        @Override
        public String toString() {
            return "Received[method=" + method + ", path=" + path + ", authorization=***]";
        }
    }

    private final HttpServer server;
    private final ExecutorService executor;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final CountDownLatch closed = new CountDownLatch(1);
    private volatile ToIntFunction<Received> responder = request -> UNAVAILABLE;
    private volatile Duration delay = Duration.ZERO;

    /** Yürütücü bu nesneye aittir ve {@link #close()} ile kapatılır; sunucu ilk istekten önce ona bağlanır. */
    private ErasureTargetStub(HttpServer server) {
        this.server = server;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        server.createContext("/", this::handle);
        server.setExecutor(executor);
    }

    public static ErasureTargetStub start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            ErasureTargetStub stub = new ErasureTargetStub(server);
            server.start();
            return stub;
        } catch (IOException exception) {
            throw new IllegalStateException("Erasure target stub could not start", exception);
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void respondWith(int status) {
        responder = request -> status;
    }

    public void respondWith(ToIntFunction<Received> statusForRequest) {
        responder = statusForRequest;
    }

    public void delay(Duration responseDelay) {
        delay = responseDelay;
    }

    public List<Received> received() {
        return List.copyOf(received);
    }

    /** Varsayılan davranışa döner (503, gecikme yok) ve kayıtları temizler. */
    public void reset() {
        responder = request -> UNAVAILABLE;
        delay = Duration.ZERO;
        received.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            Received request = new Received(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"));
            received.add(request);
            int status = awaitDelay() ? responder.applyAsInt(request) : UNAVAILABLE;
            if (status >= 300 && status < 400) {
                exchange.getResponseHeaders().set("Location", baseUrl() + REDIRECT_PATH);
            }
            exchange.sendResponseHeaders(status, NO_BODY);
        }
    }

    /**
     * Yanıtı geciktirir (zaman aşımı testi). Taklit beklerken kapatılırsa bekleme hemen biter.
     *
     * @return gecikme tamamlandıysa {@code true}; taklit kapandıysa veya kesildiyse {@code false}
     */
    private boolean awaitDelay() {
        if (delay.isZero()) {
            return true;
        }
        try {
            return !closed.await(delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void close() {
        closed.countDown();
        server.stop(0);
        executor.close();
    }
}
