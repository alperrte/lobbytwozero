package com.ltz.game.application.service;

import com.ltz.game.domain.exception.ProviderException;
import java.time.Duration;
import java.util.Set;

/**
 * Fiyat yenilemesinde hata sınıflandırması ve bekleme politikası. Kota ve yerel kısıtlama oyunun hatası değildir; oyuna ya da yanıta özgü veri
 * hataları uzun, geçici hatalar kısa bir bekleme alır (bekleme yazılmazsa aynı parti her turda yeniden başa gelip pazarı tıkar).
 */
final class PriceFailurePolicy {
    /** ITAD'in kendi 429'u yüzünden blok ({@code ItadPriceClient.PROVIDER_BLOCKED} ile aynı kod). */
    static final String PROVIDER_BLOCKED = "PRICE_PROVIDER_BLOCKED";
    /** Kendi yerel pencere sınırımız doldu ({@code ItadRequestLimiter.LOCAL_THROTTLE} ile aynı kod). */
    static final String LOCAL_THROTTLE = "PRICE_LOCAL_THROTTLE";
    /** ITAD 429'u. Oyunun hatası değildir: oyunlar FAILED yazılmaz (aksi halde NO_OFFER durumları bozulur); blok istemcide tutulur. */
    static final String RATE_LIMITED = "PRICE_RATE_LIMITED";
    static final String INVALID_RESPONSE = "INVALID_PRICE_RESPONSE";
    /** Belirli oyuna özgü olabilen veri hataları: parti bölünerek zehirli oyun ayıklanır. */
    static final Set<String> DATA_ERRORS = Set.of(INVALID_RESPONSE, "AMBIGUOUS_PRICE_MAPPING", "PRICE_CURRENCY_UNAVAILABLE", "PRICE_STALE_SOURCE", "PRICE_MAPPING_LIMIT");
    /** Tek oyuna özgü veri hatasında yeniden deneme beklemesi (aynı oyun her turda başa gelip pazarı tıkamasın). */
    static final Duration DATA_ERROR_BACKOFF = Duration.ofHours(6);
    /** Partinin tamamı veri hatası verdiyse (oyuna özgü olduğu kanıtlanamadı) kısa bekleme. */
    static final Duration WHOLE_BATCH_BACKOFF = Duration.ofMinutes(15);
    static final Duration UNAVAILABLE_BACKOFF = Duration.ofMinutes(2);
    static final Duration ACCESS_BACKOFF = Duration.ofMinutes(15);
    /** Zehirli oyun ayıklamak için bir pazar turunda en çok bu kadar ek parti yenilenir (kota harcar; geçici hatada ayıklama yapılmaz). */
    static final int ISOLATION_BATCH_BUDGET = 16;

    private PriceFailurePolicy() { }

    /** Doğrulama hataları (Quote kurucusu, publish) sağlayıcı yanıtının geçersizliği olarak sınıflanır. */
    static ProviderException asProvider(RuntimeException error) {
        return error instanceof ProviderException provider ? provider : new ProviderException(INVALID_RESPONSE);
    }

    /** Günlük için hata nedeninin sınıf adı (ileti ve ayrıntı içermez). */
    static String causeName(Throwable error) {
        Throwable cause = error.getCause();
        return cause == null ? "?" : cause.getClass().getSimpleName();
    }

    /** Kota ve yerel kısıtlama oyunun hatası değildir: kayıt yazılmaz, sonraki turda aynı adaylardan sürer. */
    static boolean isTransient(String code) { return LOCAL_THROTTLE.equals(code) || PROVIDER_BLOCKED.equals(code) || RATE_LIMITED.equals(code); }

    static boolean isDataError(String code) { return DATA_ERRORS.contains(code); }

    /** Sağlayıcı {@code Retry-After} verdiyse o, yoksa hata türüne göre varsayılan bekleme. */
    static Duration backoffFor(ProviderException error) {
        if (error.retryAfter() != null) return error.retryAfter();
        if (isDataError(error.code())) return DATA_ERROR_BACKOFF;
        if ("PRICE_ACCESS_DENIED".equals(error.code()) || "PRICE_NOT_CONFIGURED".equals(error.code())) return ACCESS_BACKOFF;
        return UNAVAILABLE_BACKOFF;
    }
}
