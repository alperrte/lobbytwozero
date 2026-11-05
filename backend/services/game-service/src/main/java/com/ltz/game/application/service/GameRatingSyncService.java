package com.ltz.game.application.service;

import com.ltz.game.application.port.ReviewRatingPort;
import com.ltz.game.application.port.ServiceTokenPort;
import com.ltz.game.domain.exception.ReviewRatingUnavailableException;
import com.ltz.game.infrastructure.config.GameRatingProperties;
import com.ltz.game.infrastructure.persistence.repository.GameRatingProjectionRepository;
import java.time.Clock;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * GAME-04/05: Review'ın puan dışa aktarma ucunu baştan sona tarayıp Game'in kendi puan projeksiyonuna yazar.
 *
 * <p>Protokol (docs-review/REVIEW_GAME_SERVICE_DEVIR.md §3.4): sayfalar {@code nextAfter} boşalana kadar sırayla
 * çekilir ve upsert edilir. Tarama yalnız <b>hatasız tamamlanırsa</b> görülmeyen satırlar puansız yapılır; yarıda
 * kesilen taramada (hata, zaman aşımı, 401/403, kiralama kaybı) hiçbir satır sıfırlanmaz. Çok örnekli dağıtımda
 * tek tarayıcıyı veritabanı kiralaması sağlar. Bu sınıf transaction açmaz: Review çağrıları sırasında DB bağlantısı
 * tutulmaz, her sayfa kendi kısa transaction'ında yazılır.
 */
@Service @RequiredArgsConstructor
public class GameRatingSyncService {

    public enum Outcome { NOT_CONFIGURED, ALREADY_RUNNING, COMPLETED, FAILED }

    /** Sonsuz döngüye karşı üst sınır: 10.000 sayfa × 500 = 5 milyon puanlı oyun. */
    static final int MAX_PAGES = 10_000;
    static final String LEASE_LOST = "LEASE_LOST";
    static final String SYNC_ERROR = "SYNC_ERROR";

    private static final Logger LOG = LoggerFactory.getLogger(GameRatingSyncService.class);

    private final ReviewRatingPort reviews;
    private final ServiceTokenPort tokens;
    private final GameRatingProjectionRepository repository;
    private final GameRatingProperties properties;
    private final Clock clock;
    private final AtomicBoolean notConfiguredLogged = new AtomicBoolean();

    public Outcome synchronize() {
        if (!tokens.isConfigured()) {
            // İstemci kapalı: Review'a istek atılmaz. Operatörün nedenini görmesi için süreç başına bir kez uyarılır.
            if (notConfiguredLogged.compareAndSet(false, true)) {
                LOG.warn("LTZ puan taraması kapalı: Auth istemci gizli anahtarı yapılandırılmamış.");
            }
            return Outcome.NOT_CONFIGURED;
        }
        UUID owner = UUID.randomUUID();
        OptionalLong started = repository.startScan(owner, properties.sync().leaseTtl());
        if (started.isEmpty()) {
            return Outcome.ALREADY_RUNNING;
        }
        long scan = started.getAsLong();
        try {
            scanAllPages(owner, scan);
            if (!repository.complete(owner, scan)) {
                LOG.warn("LTZ puan taraması kiralamayı kaybetti; hiçbir satır sıfırlanmadı.");
                return Outcome.FAILED;
            }
            return Outcome.COMPLETED;
        } catch (RuntimeException exception) {
            String code = code(exception);
            // İleti yalnız sabit kod ve istisna türünü taşır; token, gizli anahtar veya yanıt gövdesi yazılmaz.
            LOG.warn("LTZ puan taraması tamamlanamadı: code={} type={}", code, exception.getClass().getSimpleName());
            fail(owner, scan, code);
            return Outcome.FAILED;
        }
    }

    private void scanAllPages(UUID owner, long scan) {
        UUID after = null;
        int pages = 0;
        do {
            if (++pages > MAX_PAGES) {
                throw new ReviewRatingUnavailableException(ReviewRatingUnavailableException.INVALID_RESPONSE);
            }
            ReviewRatingPort.RatingPage page = reviews.fetchPage(after, properties.pageSize());
            repository.upsertPage(scan, page.items(), clock.instant());
            if (!repository.extendLease(owner, scan, properties.sync().leaseTtl())) {
                throw new LeaseLostException();
            }
            after = page.nextAfter();
        } while (after != null);
    }

    private void fail(UUID owner, long scan, String code) {
        try {
            repository.fail(owner, scan, code);
        } catch (RuntimeException failure) {
            // Durum yazılamazsa kiralama süresi dolunca bir sonraki tarama devralır; bu sessizce yutulmaz.
            LOG.warn("LTZ puan taraması hata durumu kaydedilemedi: type={}", failure.getClass().getSimpleName());
        }
    }

    private static String code(RuntimeException exception) {
        if (exception instanceof ReviewRatingUnavailableException known) {
            return known.code();
        }
        return exception instanceof LeaseLostException ? LEASE_LOST : SYNC_ERROR;
    }

    private static final class LeaseLostException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        LeaseLostException() {
            super("Scan lease lost");
        }
    }
}
