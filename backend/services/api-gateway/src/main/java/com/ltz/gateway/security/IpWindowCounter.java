package com.ltz.gateway.security;

import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;

/**
 * İstemci başına dakikalık sabit pencere sayacı. Sayaç bu Gateway örneğinin belleğindedir; çok örnekli kurulumda
 * örnek başına uygulanır.
 */
final class IpWindowCounter {

    static final long WINDOW_MILLIS = 60_000L;
    private static final int PURGE_THRESHOLD = 50_000;

    private record Window(long start, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /** Doğrulanmış istemci IP'si (başlık temizleyici koyar); yoksa doğrudan bağlantı adresi. */
    static String clientKey(HttpServletRequest request) {
        Object attribute = request.getAttribute(ClientRequestSanitizerFilter.CLIENT_IP_ATTRIBUTE);
        return attribute == null ? request.getRemoteAddr() : attribute.toString();
    }

    /**
     * Bir istek sayar.
     *
     * @return sınır aşıldıysa beklenecek saniye (en az 1); aşılmadıysa 0
     */
    long hit(String key, long now, int limit) {
        long start = now - (now % WINDOW_MILLIS);
        if (windows.size() > PURGE_THRESHOLD) {
            windows.entrySet().removeIf(e -> e.getValue().start() != start);
        }
        Window window = windows.merge(key, new Window(start, 1),
                (old, fresh) -> old.start() == start ? new Window(start, old.count() + 1) : fresh);
        if (window.count() <= limit) {
            return 0;
        }
        return Math.max(1, (start + WINDOW_MILLIS - now + 999) / 1000);
    }
}
