package com.ltz.game.application.service;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.application.port.GameProvider.ReleaseStatus;
import com.ltz.game.application.port.ProviderPriority;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameMetadataProvenance;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.GameStatus;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.enums.MetadataDataState;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.config.GameRefreshProperties;
import com.ltz.game.infrastructure.persistence.repository.GameExternalReferenceRepository;
import com.ltz.game.infrastructure.persistence.repository.GameProviderSyncStateRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Oyunların çıkış durumunu (erken erişim, ücretsiz, henüz çıkmamış) Steam'in toplu mağaza verisinden alır ({@code release.is_early_access},
 * {@code is_free}, {@code release.is_coming_soon}; 100 oyun/istek). IGDB bu bilgileri güvenilir vermediği için tek kaynak Steam'dir. Kurallar:
 * <ul>
 *   <li>Değer yalnız Steam oyunu tanıdığında yazılır; tanımadığı oyun bilinmiyor (null) kalır, "erken erişim/ücretsiz/yakında değil" varsayılmaz.</li>
 *   <li>{@code free} ve {@code comingSoon} Steam'e aittir: tanınan oyunda her zaman Steam'in değeri yazılır (oyun çıkınca "yakında" kalkar).</li>
 *   <li>{@code early_access} için yönetici veya elle girilen değer ezilmez: yalnız boş değer ya da önceki Steam gözlemiyle yazılmış değer
 *       güncellenir (böylece oyun erken erişimden çıkınca false olur).</li>
 *   <li>Her inceleme {@code RELEASE_STATUS} gözlemiyle işaretlenir. Oyun, kademesinin aralığı dolunca yeniden sorulur: sıcak oyunlar
 *       ({@link GameRefreshProperties}) kısa, diğerleri uzun aralıkla. Sağlayıcı HATASI işaret yazmaz, tarama artan beklemeyle duraklar.</li>
 * </ul>
 */
@Service
public class SteamReleaseStatusService {
    static final int BATCH = 100;
    static final String MARKER = "RELEASE_STATUS";
    static final String EARLY_ACCESS_FIELD = "EARLY_ACCESS";
    /** Steam'in oyunu ilk tanımadığı an (zincir tanınma ile silinir). */
    static final String UNRECOGNISED_MARKER = "STEAM_UNRECOGNISED";
    static final String GAME_TYPE_FIELD = "GAME_TYPE";
    static final String STEAM_TYPE_MARKER = "STEAM_TYPE";
    static final int STEAM_GAME_TYPE = 0;
    /** İlk "tanınmadı" gözleminden en az bu kadar sonra ikinci gözlem gelmeden oyun mağazadan kaldırılmış sayılmaz. */
    static final Duration DELIST_CONFIRMATION = Duration.ofHours(20);
    private static final String PROVIDER_CODE = "STEAM";
    private static final Duration MAX_PAUSE = Duration.ofHours(1);
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SteamReleaseStatusService.class);

    /** Bir oyunun Steam AppID'si. */
    private record Target(String externalId, UUID gameId) { }

    private final GameProvider provider;
    private final GameRepository games;
    private final GameExternalReferenceRepository references;
    private final GameProviderSyncStateRepository states;
    private final MetadataProvenanceService provenance;
    private final ProviderRetryPolicy retries;
    private final GameRefreshProperties refresh;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private Instant pausedUntil;
    private int consecutiveFailures;
    private long updated;
    private long unknown;

    public SteamReleaseStatusService(GameProvider provider, GameRepository games, GameExternalReferenceRepository references,
            GameProviderSyncStateRepository states, MetadataProvenanceService provenance, ProviderRetryPolicy retries,
            GameRefreshProperties refresh, PlatformTransactionManager transactions, Clock clock) {
        this.provider = provider;
        this.games = games;
        this.references = references;
        this.states = states;
        this.provenance = provenance;
        this.retries = retries;
        this.refresh = refresh;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /** Yalnız testler için. */
    synchronized void resetForTests() {
        pausedUntil = null;
        consecutiveFailures = 0;
        updated = 0;
        unknown = 0;
    }

    /** Boşta kalındığında sıcak aralığın üçte biri kadar bekler (en az 1, en çok 5 dakika): sıcak oyunlar vaktinde yeniden sorulur. */
    private Duration idleInterval() {
        Duration third = refresh.release().hot().dividedBy(3);
        if (third.compareTo(Duration.ofMinutes(1)) < 0) return Duration.ofMinutes(1);
        return third.compareTo(Duration.ofMinutes(5)) > 0 ? Duration.ofMinutes(5) : third;
    }

    /** @return bu adımda işlenen oyun sayısı; iş yoksa veya duraklatılmışsa {@code -1} */
    public synchronized int nextBatch() {
        Instant now = clock.instant();
        if (pausedUntil != null && now.isBefore(pausedUntil)) return -1;
        // Steam kotasının arka plan payı dolduysa yalnız sıcak oyunlar (öncelikli) incelenir; diğerleri kota yenilenince sürer.
        boolean hotOnly = !provider.backgroundWorkAllowed();
        var rows = references.releaseStatusCandidates(now.minus(refresh.release().hot()), now.minus(refresh.release().other()),
                refresh.hotPastDays(), refresh.hotFutureDays(), hotOnly, BATCH).stream()
                .map(row -> new Target(row.getExternalId(), row.getGameId())).toList();
        if (rows.isEmpty()) {
            if (updated + unknown > 0) LOG.info("Steam çıkış durumu taraması tamamlandı: değişen={} bilinmeyen={}", updated, unknown);
            updated = 0;
            unknown = 0;
            pausedUntil = now.plus(idleInterval());
            return -1;
        }
        return hotOnly ? ProviderPriority.essential(() -> fetchAndApply(rows, now)) : fetchAndApply(rows, now);
    }

    /**
     * Sayfası açılan oyunlar için hemen yenileme (ipucu tahliyesi): verilen oyunlardan Steam referanslı olanlar tek toplu çağrıyla sorulur.
     *
     * @return işlenen oyun sayısı; duraklatılmışsa veya hata olursa {@code -1}
     */
    public synchronized int refreshNow(Collection<UUID> gameIds) {
        Instant now = clock.instant();
        if (gameIds.isEmpty() || (pausedUntil != null && now.isBefore(pausedUntil))) return -1;
        var rows = references.findByGameIdIn(gameIds).stream().filter(ref -> PROVIDER_CODE.equals(ref.getProvider()))
                .map(ref -> new Target(ref.getExternalId(), ref.getGame().getId())).limit(BATCH).toList();
        return rows.isEmpty() ? -1 : fetchAndApply(rows, now);
    }

    private int fetchAndApply(List<Target> rows, Instant now) {
        Map<String, ReleaseStatus> found;
        try {
            found = provider.releaseStatus(rows.stream().map(Target::externalId).toList());
        } catch (ProviderException error) {
            pause(error, now);
            return -1;
        }
        consecutiveFailures = 0;
        return Objects.requireNonNullElse(transaction.execute(status -> apply(rows, found)), -1);
    }

    private int apply(List<Target> rows, Map<String, ReleaseStatus> found) {
        states.lockProvider(PROVIDER_CODE).orElseThrow();
        games.lockCatalogGames(PROVIDER_CODE, rows.stream().map(Target::externalId).toList());
        Map<UUID, Game> loaded = games.findAllById(rows.stream().map(Target::gameId).toList()).stream()
                .collect(Collectors.toMap(Game::getId, Function.identity()));
        int processed = 0;
        for (var row : rows) {
            Game game = loaded.get(row.gameId());
            if (!scannable(game)) continue;
            applyRow(game, found.get(row.externalId()));
            processed++;
        }
        return processed;
    }

    /** Aktif, harici ana oyun: çıkış durumu taramasının tek hedefi. */
    private static boolean scannable(Game game) {
        return game != null && game.getStatus() == GameStatus.ACTIVE && game.getOrigin() == GameOrigin.EXTERNAL && game.getGameType() == GameType.MAIN_GAME;
    }

    /** Bir oyunun sonucu: Steam tanıdıysa değerler (ve tür doğrulaması) yazılır, tanımadıysa bilinmiyor kalır ve delist zinciri ilerler; her durumda gözlem işaretlenir. */
    private void applyRow(Game game, ReleaseStatus status) {
        if (status != null) {
            boolean changed = applyEarlyAccess(game, status.earlyAccess());
            changed |= game.applySteamReleaseStatus(status.free(), status.comingSoon(), status.adult());
            changed |= recognised(game);
            changed |= verifyType(game, status);
            if (changed) {
                game.setUpdatedAt(clock.instant());
                updated++;
            }
        } else {
            if (game.getEarlyAccess() == null && game.getMetadataProvenance().get(EARLY_ACCESS_FIELD) == null) {
                provenance.recordObservation(game, EARLY_ACCESS_FIELD, PROVIDER_CODE, MetadataDataState.UNKNOWN, false);
            }
            if (unrecognised(game)) game.setUpdatedAt(clock.instant());
            unknown++;
        }
        provenance.recordObservation(game, MARKER, PROVIDER_CODE, status != null ? MetadataDataState.AVAILABLE : MetadataDataState.UNKNOWN, false);
    }

    /** Steam oyunu tanıdı: bekleyen "tanınmadı" gözlemini siler ve varsa mağazadan kaldırıldı işaretini kaldırır. */
    private boolean recognised(Game game) {
        game.getMetadataProvenance().remove(UNRECOGNISED_MARKER);
        return game.clearDelisted();
    }

    /**
     * Steam oyunu tanımadı. Yanlış işaretlemeyi önlemek için ilk gözlem yalnız not edilir (özgün zaman korunur, yenilenmez); aynı durum en az
     * {@link #DELIST_CONFIRMATION} sonra yeniden gözlenirse oyun mağazadan kaldırılmış sayılır. Tanınma ya da sağlayıcı hatası zinciri bozar.
     *
     * @return oyun bu çağrıda mağazadan kaldırıldı olarak işaretlendiyse true
     */
    private boolean unrecognised(Game game) {
        GameMetadataProvenance first = game.getMetadataProvenance().get(UNRECOGNISED_MARKER);
        Instant now = clock.instant();
        if (first == null) {
            provenance.recordObservation(game, UNRECOGNISED_MARKER, PROVIDER_CODE, MetadataDataState.UNKNOWN, false);
            return false;
        }
        return !first.getRecordedAt().plus(DELIST_CONFIRMATION).isAfter(now) && game.markDelisted(now);
    }

    /**
     * Steam kaynaklı sınıflamada tür kayması koruması: tür kanıtı Steam olan ana oyun için Steam artık "oyun" (type=0) demiyorsa tür bilinmiyora
     * döner ve yeniden sınıflanır. Yönetici ya da IGDB kanıtlı türe dokunulmaz.
     *
     * @return tür sıfırlandıysa true
     */
    private boolean verifyType(Game game, ReleaseStatus status) {
        GameMetadataProvenance type = game.getMetadataProvenance().get(GAME_TYPE_FIELD);
        if (status.storeType() == null || status.storeType() == STEAM_GAME_TYPE || type == null || !PROVIDER_CODE.equals(type.getSource())) return false;
        game.setGameType(GameType.UNKNOWN);
        provenance.recordObservation(game, GAME_TYPE_FIELD, PROVIDER_CODE, MetadataDataState.UNKNOWN, false);
        game.getMetadataProvenance().remove(STEAM_TYPE_MARKER);
        return true;
    }

    /** Yönetici değerini ezmeden erken erişimi günceller; Steam'in yazdığı değer (AVAILABLE gözlemi) oyun çıkınca false olur. */
    private boolean applyEarlyAccess(Game game, boolean flag) {
        GameMetadataProvenance previous = game.getMetadataProvenance().get(EARLY_ACCESS_FIELD);
        boolean managed = previous != null && previous.getDataState() == MetadataDataState.AVAILABLE;
        if (game.getEarlyAccess() == null || (managed && flag != game.getEarlyAccess())) {
            game.setEarlyAccess(flag);
            provenance.recordObservation(game, EARLY_ACCESS_FIELD, PROVIDER_CODE, MetadataDataState.AVAILABLE, false);
            return true;
        }
        if (previous == null) provenance.recordObservation(game, EARLY_ACCESS_FIELD, PROVIDER_CODE, MetadataDataState.UNKNOWN, false);
        return false;
    }

    private void pause(ProviderException error, Instant now) {
        consecutiveFailures = Math.min(consecutiveFailures + 1, 6);
        Duration delay = retries.delay(error, Math.min(consecutiveFailures, 5));
        long factor = consecutiveFailures >= 2 ? 4L : 1L;
        Duration pause = delay == null ? MAX_PAUSE : delay.multipliedBy(factor);
        if (pause.compareTo(Duration.ofMinutes(1)) < 0) pause = Duration.ofMinutes(1);
        if (pause.compareTo(MAX_PAUSE) > 0) pause = MAX_PAUSE;
        pausedUntil = now.plus(pause);
        LOG.warn("Steam çıkış durumu taraması duraklatıldı: kod={} ardışıkHata={} devam={}", error.code(), consecutiveFailures, pausedUntil);
    }
}
