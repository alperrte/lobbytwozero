package com.ltz.game.infrastructure.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpHeaders;

/**
 * ITAD istek ve kota gözlemi: yalnız ÖLÇER, hiçbir karar vermez. Gerçek ITAD kotası belgelenmediği ve 429 anında kaç istek atıldığı bilinmediği için
 * (DEV anahtarı ekip tarafından paylaşılır) sınırı doğru ayarlamak için gereken veriyi üretir: her 429 tek satırda pencere doluluğuyla loglanır, dakikada
 * bir özet satırı istek türlerini ve gecikmeyi verir. Anahtar, URL ve yanıt gövdesi ASLA loglanmaz; etiketler kodda üretilir.
 */
final class ItadTelemetry {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ItadTelemetry.class);
    static final Duration SUMMARY_EVERY = Duration.ofSeconds(60);
    static final Duration HEADER_LOG_EVERY = Duration.ofSeconds(60);
    private static final int RAW_MAX = 40;

    /** Bir ITAD çağrısının bağlamı: istek türü ({@code prices}/{@code lookup}), pazar ve öncelik. */
    record Call(String kind, String market, boolean interactive) {
        String priority() { return interactive ? "INTERACTIVE" : "BACKGROUND"; }
    }

    private final Clock clock;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger consecutive429 = new AtomicInteger();
    private final AtomicReference<Instant> last429 = new AtomicReference<>();
    private final AtomicReference<Instant> lastSummary;
    private final AtomicReference<Instant> lastHeaderLog = new AtomicReference<>(Instant.MIN);
    private final AtomicLong prices = new AtomicLong();
    private final AtomicLong lookups = new AtomicLong();
    private final AtomicLong tooMany = new AtomicLong();
    private final AtomicLong otherErrors = new AtomicLong();
    private final AtomicLong latencyMillis = new AtomicLong();

    ItadTelemetry(Clock clock) {
        this.clock = clock;
        this.lastSummary = new AtomicReference<>(clock.instant());
    }

    /** Çağrı başlar; {@link #finish} ile çifttir. */
    void start() { inFlight.incrementAndGet(); }

    int inFlight() { return inFlight.get(); }

    /** Çağrı biter. {@code status}: HTTP durumu, ağ hatasında 0. */
    void finish(Call call, int status, long millis) {
        inFlight.decrementAndGet();
        if ("lookup".equals(call.kind())) lookups.incrementAndGet(); else prices.incrementAndGet();
        latencyMillis.addAndGet(millis);
        if (status == 429) tooMany.incrementAndGet();
        else if (status != 200) otherErrors.incrementAndGet();
        if (status == 200) consecutive429.set(0);
        summarizeIfDue();
    }

    /** ITAD 429 verdi: pencere doluluğuyla tek satır. {@code retryAfterRaw} ham başlıktır ve güvenli karakterlerle kısaltılır. */
    void rateLimited(Call call, ItadRequestLimiter.Snapshot window, String retryAfterRaw, Duration appliedBlock, int newEffectiveLimit) {
        Instant now = clock.instant();
        Instant previous = last429.getAndSet(now);
        int streak = consecutive429.incrementAndGet();
        if (LOG.isWarnEnabled()) {
            LOG.warn("ITAD kotasi: olay=429 yol={} pazar={} oncelik={} pencere_ici={} son60sn={} son10sn={} eszamanli={} limit={} etkin_once={} etkin_sonra={} rezerv={} retry_after_ham=\"{}\" engel_sn={} ardisik={} onceki_429_sn={}",
                    call.kind(), call.market(), call.priority(), window.inWindow(), window.last60s(), window.last10s(), inFlight.get(), window.limit(), window.effective(), newEffectiveLimit, window.reserve(),
                    sanitize(retryAfterRaw), appliedBlock.toSeconds(), streak, previous == null ? -1 : Duration.between(previous, now).toSeconds());
        }
    }

    /**
     * Başarılı yanıttaki hız sınırı başlıkları (varsa). ITAD'in bunları verdiği doğrulanmadı: yalnız GÖZLEMLENİR, kod bunlarla karar vermez.
     * Dakikada en çok bir kez loglanır.
     */
    void observeHeaders(HttpHeaders headers) {
        StringBuilder found = new StringBuilder();
        headers.forEach((name, values) -> {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (lower.startsWith("x-ratelimit") || lower.startsWith("ratelimit")) {
                if (!found.isEmpty()) found.append(' ');
                found.append(sanitizeName(lower)).append("=\"").append(sanitize(String.join(",", values))).append('"');
            }
        });
        if (found.isEmpty()) return;
        Instant now = clock.instant();
        Instant last = lastHeaderLog.get();
        if (last.plus(HEADER_LOG_EVERY).isAfter(now) || !lastHeaderLog.compareAndSet(last, now)) return;
        LOG.info("ITAD kotasi: olay=baslik {}", found);
    }

    private void summarizeIfDue() {
        Instant now = clock.instant();
        Instant last = lastSummary.get();
        if (last.plus(SUMMARY_EVERY).isAfter(now) || !lastSummary.compareAndSet(last, now)) return;
        long p = prices.getAndSet(0);
        long l = lookups.getAndSet(0);
        long e = tooMany.getAndSet(0);
        long o = otherErrors.getAndSet(0);
        long ms = latencyMillis.getAndSet(0);
        long total = p + l;
        if (total == 0) return;
        LOG.info("ITAD ozet: sure_sn={} prices={} lookup={} e429={} diger_hata={} ort_ms={}",
                Duration.between(last, now).toSeconds(), p, l, e, o, ms / total);
    }

    /** Ham değeri günlüğe güvenli kılar: yalnız harf, rakam, boşluk, virgül, iki nokta; en çok {@value #RAW_MAX} karakter. */
    static String sanitize(String raw) {
        if (raw == null) return "yok";
        String clean = raw.replaceAll("[^0-9A-Za-z ,:]", "");
        return clean.length() > RAW_MAX ? clean.substring(0, RAW_MAX) : clean;
    }

    private static String sanitizeName(String name) { return name.replaceAll("[^a-z0-9-]", ""); }
}
