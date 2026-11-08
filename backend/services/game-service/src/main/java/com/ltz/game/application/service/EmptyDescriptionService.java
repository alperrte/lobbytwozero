package com.ltz.game.application.service;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.GameStatus;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.enums.MetadataDataState;
import com.ltz.game.domain.enums.OverridableField;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.persistence.repository.GameExternalReferenceRepository;
import com.ltz.game.infrastructure.persistence.repository.GameFieldOverrideRepository;
import com.ltz.game.infrastructure.persistence.repository.GameProviderSyncStateRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Açıklaması tamamen BOŞ olan ana oyunlar için (IGDB özeti yok) Steam'in İngilizce kısa mağaza açıklamasını yedek kaynak olarak yazar.
 * Kurallar: yalnız boş/yalnız-boşluk açıklama doldurulur; mevcut açıklama ve yönetici metni asla değiştirilmez (manuel {@code DESCRIPTION} kilidi
 * de korunur); metin gerçekten İngilizce görünmelidir ({@link DescriptionLanguage#looksEnglish}), aksi halde yazılmaz (başka dildeki metin
 * "İngilizce" diye işaretlenmez); Steam metin vermezse metin uydurulmaz. Kaynak provenance'a {@code DESCRIPTION/STEAM} yazılır; böylece API
 * {@code descriptionLanguage="en"} döner. Her inceleme {@code STEAM_DESC_EN} gözlemiyle işaretlenir (30 gün sonra yeniden bakılır); sağlayıcı
 * HATASI işaret yazmaz, tarama duraklar. İdempotenttir; yeni migration gerekmez.
 */
@Service
public class EmptyDescriptionService {
    static final int BATCH = 100;
    static final Duration RECHECK = Duration.ofDays(30);
    static final Duration IDLE_INTERVAL = Duration.ofMinutes(30);
    static final String MARKER = "STEAM_DESC_EN";
    private static final String PROVIDER_CODE = "STEAM";
    private static final Duration MAX_PAUSE = Duration.ofHours(1);
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(EmptyDescriptionService.class);

    private final GameProvider provider;
    private final GameRepository games;
    private final GameExternalReferenceRepository references;
    private final GameFieldOverrideRepository overrides;
    private final GameProviderSyncStateRepository states;
    private final MetadataProvenanceService provenance;
    private final ProviderRetryPolicy retries;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private Instant pausedUntil;
    private int consecutiveFailures;
    private long filled;
    private long unavailable;

    public EmptyDescriptionService(GameProvider provider, GameRepository games, GameExternalReferenceRepository references,
            GameFieldOverrideRepository overrides, GameProviderSyncStateRepository states, MetadataProvenanceService provenance,
            ProviderRetryPolicy retries, PlatformTransactionManager transactions, Clock clock) {
        this.provider = provider;
        this.games = games;
        this.references = references;
        this.overrides = overrides;
        this.states = states;
        this.provenance = provenance;
        this.retries = retries;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /** Yalnız testler için. */
    synchronized void resetForTests() {
        pausedUntil = null;
        consecutiveFailures = 0;
        filled = 0;
        unavailable = 0;
    }

    /** @return bu adımda işlenen oyun sayısı; iş yoksa veya duraklatılmışsa {@code -1} */
    public synchronized int nextBatch() {
        Instant now = clock.instant();
        if (pausedUntil != null && now.isBefore(pausedUntil)) return -1;
        var rows = references.emptyDescriptionCandidates(MARKER, now.minus(RECHECK), BATCH);
        if (rows.isEmpty()) {
            if (filled + unavailable > 0) LOG.info("Boş açıklama yedeği tamamlandı: doldurulan={} Steam-metni-yok/uygunsuz={}", filled, unavailable);
            filled = 0;
            unavailable = 0;
            pausedUntil = now.plus(IDLE_INTERVAL);
            return -1;
        }
        Map<String, String> found;
        try {
            found = provider.shortDescriptions(rows.stream().map(row -> row.getExternalId()).toList(), LocalizedDescriptionService.DEFAULT_LANGUAGE);
        } catch (ProviderException error) {
            pause(error, now);
            return -1;
        }
        consecutiveFailures = 0;
        Integer processed = transaction.execute(status -> apply(rows, found, now));
        return processed == null ? -1 : processed;
    }

    private int apply(List<GameExternalReferenceRepository.ClassificationRow> rows, Map<String, String> found, Instant now) {
        states.lockProvider(PROVIDER_CODE).orElseThrow();
        games.lockCatalogGames(PROVIDER_CODE, rows.stream().map(row -> row.getExternalId()).toList());
        Map<UUID, Game> loaded = games.findAllById(rows.stream().map(row -> row.getGameId()).toList()).stream()
                .collect(Collectors.toMap(Game::getId, Function.identity()));
        int processed = 0;
        for (var row : rows) {
            Game game = loaded.get(row.getGameId());
            if (game == null || game.getStatus() != GameStatus.ACTIVE || game.getOrigin() != GameOrigin.EXTERNAL || game.getGameType() != GameType.MAIN_GAME) continue;
            String text = found.get(row.getExternalId());
            // Kilit altında yeniden denetim: bu arada açıklama yazıldıysa (yönetici, IGDB) hiç dokunulmaz.
            boolean empty = game.getDescription() == null || game.getDescription().isBlank();
            Set<OverridableField> locked = EnumSet.noneOf(OverridableField.class);
            overrides.findByGameIdOrderByFieldAsc(game.getId()).forEach(override -> locked.add(override.getField()));
            boolean write = empty && text != null && !locked.contains(OverridableField.DESCRIPTION) && DescriptionLanguage.looksEnglish(text);
            if (write) {
                game.setDescription(text);
                provenance.recordObservation(game, "DESCRIPTION", "STEAM", MetadataDataState.AVAILABLE, false);
                game.setUpdatedAt(now);
                filled++;
            } else if (empty) {
                unavailable++;
            }
            provenance.recordObservation(game, MARKER, "STEAM", write ? MetadataDataState.AVAILABLE : MetadataDataState.EMPTY, false);
            processed++;
        }
        return processed;
    }

    private void pause(ProviderException error, Instant now) {
        consecutiveFailures = Math.min(consecutiveFailures + 1, 6);
        Duration delay = retries.delay(error, Math.min(consecutiveFailures, 5));
        Duration pause = delay == null ? MAX_PAUSE : delay.multipliedBy(consecutiveFailures >= 2 ? 4L : 1L);
        if (pause.compareTo(Duration.ofMinutes(1)) < 0) pause = Duration.ofMinutes(1);
        if (pause.compareTo(MAX_PAUSE) > 0) pause = MAX_PAUSE;
        pausedUntil = now.plus(pause);
        LOG.warn("Boş açıklama yedeği duraklatıldı: kod={} ardışıkHata={} devam={}", error.code(), consecutiveFailures, pausedUntil);
    }
}
