package com.ltz.game.application.service;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameLocalizedDescription;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.GameStatus;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.enums.MetadataDataState;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.persistence.repository.GameExternalReferenceRepository;
import com.ltz.game.infrastructure.persistence.repository.GameLocalizedDescriptionRepository;
import com.ltz.game.infrastructure.persistence.repository.GameProviderSyncStateRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ana oyunların kısa mağaza açıklamasını yapılandırılmış ek dillerde (varsayılan yalnız {@code tr}) sağlayıcıdan alıp
 * {@code game_localized_descriptions} tablosuna yazar. Kesin kurallar:
 * <ul>
 *   <li>Çeviri yoktur; harici çeviri hizmeti çağrılmaz. Yalnız sağlayıcının o dilde GERÇEKTEN verdiği metin saklanır.</li>
 *   <li>Sağlayıcı o dilde metin yoksa İngilizceye düşer. Bu yüzden aynı oyun İngilizce de sorulur ve metin İngilizceden FARKLI olmalıdır; ayrıca
 *       metin o dile özgü kanıt taşımalıdır ({@link DescriptionLanguage}). Aksi halde satır YAZILMAZ (İngilizce metin yanlışlıkla Türkçe sayılmaz).</li>
 *   <li>{@code games.description} (varsayılan/İngilizce) ve yönetici verisi hiç değiştirilmez; (oyun, dil) için tek satır vardır (var olan güncellenir).</li>
 *   <li>Yapılandırılmamış dil için istek atılmaz. Her inceleme {@code STEAM_DESC_<DİL>} gözlemiyle işaretlenir (30 gün sonra yeniden bakılır);
 *       sağlayıcı HATASI işaret yazmaz, tarama duraklar.</li>
 * </ul>
 */
@Service
public class LocalizedDescriptionService {
    static final int BATCH = 100;
    static final Duration RECHECK = Duration.ofDays(30);
    static final Duration IDLE_INTERVAL = Duration.ofMinutes(30);
    static final String DEFAULT_LANGUAGE = "en";
    private static final String PROVIDER_CODE = "STEAM";
    private static final Duration MAX_PAUSE = Duration.ofHours(1);
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(LocalizedDescriptionService.class);

    private final GameProvider provider;
    private final GameRepository games;
    private final GameExternalReferenceRepository references;
    private final GameLocalizedDescriptionRepository descriptions;
    private final GameProviderSyncStateRepository states;
    private final MetadataProvenanceService provenance;
    private final ProviderRetryPolicy retries;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final List<String> languages;
    private Instant pausedUntil;
    private int consecutiveFailures;
    private long stored;
    private long fallback;

    public LocalizedDescriptionService(GameProvider provider, GameRepository games, GameExternalReferenceRepository references,
            GameLocalizedDescriptionRepository descriptions, GameProviderSyncStateRepository states, MetadataProvenanceService provenance,
            ProviderRetryPolicy retries, PlatformTransactionManager transactions, Clock clock,
            @Value("${ltz.game.localized-descriptions.languages:tr}") String languages) {
        this.provider = provider;
        this.games = games;
        this.references = references;
        this.descriptions = descriptions;
        this.states = states;
        this.provenance = provenance;
        this.retries = retries;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
        // Yalnız dil denetimi tanımlı diller (şimdilik tr) işlenir; İngilizce varsayılandır ve tabloya yazılmaz.
        this.languages = Arrays.stream(languages.split(",")).map(code -> code.strip().toLowerCase(Locale.ROOT))
                .filter(code -> !code.isEmpty() && !DEFAULT_LANGUAGE.equals(code) && DescriptionLanguage.supports(code)).distinct().toList();
    }

    /** Yalnız testler için. */
    synchronized void resetForTests() {
        pausedUntil = null;
        consecutiveFailures = 0;
        stored = 0;
        fallback = 0;
    }

    static String marker(String language) { return "STEAM_DESC_" + language.toUpperCase(Locale.ROOT); }

    /** @return bu adımda işlenen oyun sayısı; iş yoksa, dil yapılandırılmamışsa veya duraklatılmışsa {@code -1} */
    public synchronized int nextBatch() {
        Instant now = clock.instant();
        if (pausedUntil != null && now.isBefore(pausedUntil)) return -1;
        for (String language : languages) {
            int processed = batchFor(language, now);
            if (processed >= 0) return processed;
            if (pausedUntil != null && now.isBefore(pausedUntil)) return -1;
        }
        return -1;
    }

    private int batchFor(String language, Instant now) {
        var rows = references.localizedDescriptionCandidates(marker(language), now.minus(RECHECK), BATCH);
        if (rows.isEmpty()) {
            if (stored + fallback > 0) LOG.info("Yerelleştirilmiş açıklama ({}) tamamlandı: kaydedilen={} İngilizce-fallback/yok={}", language, stored, fallback);
            stored = 0;
            fallback = 0;
            return -1;
        }
        List<String> ids = rows.stream().map(row -> row.getExternalId()).toList();
        Map<String, String> english, localized;
        try {
            english = provider.shortDescriptions(ids, DEFAULT_LANGUAGE);
            localized = provider.shortDescriptions(ids, language);
        } catch (ProviderException error) {
            pause(error, now);
            return -1;
        }
        consecutiveFailures = 0;
        Integer processed = transaction.execute(status -> apply(rows, language, english, localized, now));
        return processed == null ? -1 : processed;
    }

    private int apply(List<GameExternalReferenceRepository.ClassificationRow> rows, String language, Map<String, String> english,
            Map<String, String> localized, Instant now) {
        states.lockProvider(PROVIDER_CODE).orElseThrow();
        games.lockCatalogGames(PROVIDER_CODE, rows.stream().map(row -> row.getExternalId()).toList());
        Map<UUID, Game> loaded = games.findAllById(rows.stream().map(row -> row.getGameId()).toList()).stream()
                .collect(Collectors.toMap(Game::getId, Function.identity()));
        Map<UUID, GameLocalizedDescription> existing = descriptions.findByGamesAndLanguage(loaded.keySet(), language).stream()
                .collect(Collectors.toMap(row -> row.getGame().getId(), Function.identity()));
        int processed = 0;
        for (var row : rows) {
            Game game = loaded.get(row.getGameId());
            if (game == null || game.getStatus() != GameStatus.ACTIVE || game.getOrigin() != GameOrigin.EXTERNAL || game.getGameType() != GameType.MAIN_GAME) continue;
            String text = localized.get(row.getExternalId());
            String base = english.get(row.getExternalId());
            // İngilizce metin yoksa ayrım yapılamaz (fallback ile gerçek metin ayırt edilemez): kaydedilmez.
            boolean real = text != null && base != null && !text.equals(base) && DescriptionLanguage.accepts(language, text);
            if (real) {
                GameLocalizedDescription entry = existing.get(game.getId());
                if (entry == null) {
                    entry = new GameLocalizedDescription();
                    entry.setGame(game);
                    entry.setLanguage(language);
                }
                entry.setDescription(text);
                entry.setSource("STEAM");
                entry.setFetchedAt(now);
                descriptions.save(entry);
                stored++;
            } else {
                fallback++;
            }
            provenance.recordObservation(game, marker(language), "STEAM", real ? MetadataDataState.AVAILABLE : MetadataDataState.EMPTY, false);
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
        LOG.warn("Yerelleştirilmiş açıklama duraklatıldı: kod={} ardışıkHata={} devam={}", error.code(), consecutiveFailures, pausedUntil);
    }
}
