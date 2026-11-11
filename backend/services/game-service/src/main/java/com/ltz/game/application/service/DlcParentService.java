package com.ltz.game.application.service;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.GameStatus;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.enums.MetadataDataState;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.persistence.repository.DlcParentRepository;
import com.ltz.game.infrastructure.persistence.repository.GameExternalReferenceRepository;
import com.ltz.game.infrastructure.persistence.repository.GameProviderSyncStateRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DLC kayıtlarını ana oyunlarına bağlar. Birincil kaynak IGDB {@code parent_game}'dir; IGDB'nin oyun kimliği LTZ kimliği sayılmaz:
 * üst oyun IGDB'nin kendi Steam kaydı üzerinden Steam AppID'ye, o da katalogdaki oyuna çevrilir. Steam'in {@code related_items.parent_appid}
 * bilgisi (toplu mağaza çağrısı, 100 DLC/istek) yalnız (a) IGDB üst oyun bildirmediğinde, (b) eşleme belirsizse/eşlenemediyse, (c) örneklem
 * doğrulamasında (AppID % 25 = 0, yaklaşık %4) kullanılır. IGDB yeteneği yoksa (IGDB kapalı kurulum) IGDB adımı atlanır ve tüm adaylar
 * doğrudan Steam ile sorulur. İki kaynak çelişirse rastgele seçilmez: ilişki boş bırakılır ve CONFLICT olarak sayılır.
 * <p>
 * Bağlama kuralları: ana oyun katalogda aktif bir MAIN_GAME olmalıdır; UNKNOWN/başka türdeki ana oyuna ZORLA bağlanmaz (sınıflandırma
 * politikası bozulmaz); bağlanamayan kayıt {@link #UNKNOWN_RECHECK} sonra, kesin incelenen (çelişki vb.) kayıt {@link #RECHECK} sonra yeniden bakılır). Veritabanı kısıtları (V174) kendine bağlanmayı, DLC olmayan kayda bağlanmayı
 * ve DLC'ye üst oyun olmasını reddeder. Her inceleme {@code DLC_PARENT} gözlemiyle işaretlenir; sağlayıcı HATASI işaret yazmaz.
 */
@Service
public class DlcParentService {
    static final int BATCH = 200;
    static final Duration RECHECK = Duration.ofDays(7);
    /** Ana oyun/kanıt henüz yoksa (UNKNOWN gözlem) yeniden bakma aralığı: ana oyun sınıflanınca DLC saatler içinde bağlanır. */
    static final Duration UNKNOWN_RECHECK = Duration.ofHours(6);
    static final Duration IDLE_INTERVAL = Duration.ofMinutes(30);
    /** Steam toplu mağaza çağrısı başına en çok bu kadar DLC. */
    static final int STEAM_BATCH = 100;
    /** Örneklem doğrulaması: AppID bu sayıya tam bölünen IGDB bağlantıları Steam ile de doğrulanır (~%4). */
    static final int VERIFY_MODULUS = 25;
    static final String MARKER = "DLC_PARENT";
    private static final String PROVIDER_CODE = "STEAM";
    private static final Duration MAX_PAUSE = Duration.ofHours(1);
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DlcParentService.class);

    /** Bir DLC'nin ilişki sonucu. */
    public enum Outcome { LINKED, LINKED_VIA_STEAM, NO_PARENT_EVIDENCE, PARENT_NOT_IN_CATALOG, PARENT_NOT_CLASSIFIED, PARENT_NOT_MAIN_GAME, CONFLICT }

    /** Bir DLC için toplanmış kanıt: IGDB üst oyunu (Steam AppID olarak, yoksa null) ve Steam {@code fullgame} AppID'si (yoksa null). */
    private record Decision(GameExternalReferenceRepository.ClassificationRow row, String igdbParent, String steamParent) { }

    private record Pending(GameExternalReferenceRepository.ClassificationRow row, String igdbParent) { }

    private final GameProvider provider;
    private final GameRepository games;
    private final DlcParentRepository parents;
    private final GameProviderSyncStateRepository states;
    private final MetadataProvenanceService provenance;
    private final ProviderRetryPolicy retries;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private final Map<Outcome, Long> totals = new EnumMap<>(Outcome.class);
    private Instant pausedUntil;
    private int consecutiveFailures;
    /** Aday imleci (AppID azalan): Steam doğrulaması bekleyen kayıtlar işaretsiz olduğundan aynı partiyi yeniden döndürmesin. */
    private long before = Long.MAX_VALUE;
    private boolean steamTurn = true;

    public DlcParentService(GameProvider provider, GameRepository games, DlcParentRepository parents,
            GameProviderSyncStateRepository states, MetadataProvenanceService provenance, ProviderRetryPolicy retries,
            PlatformTransactionManager transactions, Clock clock) {
        this.provider = provider;
        this.games = games;
        this.parents = parents;
        this.states = states;
        this.provenance = provenance;
        this.retries = retries;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /** Bu süreçte (yeniden başlatmaya kadar) sonuç başına toplam; raporlama ve test içindir. */
    public synchronized Map<Outcome, Long> totals() { return new EnumMap<>(totals); }

    /** Yalnız testler için. */
    synchronized void resetForTests() {
        pending.clear();
        before = Long.MAX_VALUE;
        steamTurn = true;
        totals.clear();
        pausedUntil = null;
        consecutiveFailures = 0;
    }

    /**
     * Bir inceleme adımı: bekleyen Steam doğrulamaları varsa en çok {@value #STEAM_BATCH} tanesi tek toplu çağrıyla, yoksa bir aday partisi
     * (IGDB açıksa IGDB kanıtıyla).
     *
     * @return işlenen DLC sayısı; iş yoksa veya duraklatılmışsa {@code -1}
     */
    public synchronized int reviewNext() {
        Instant now = clock.instant();
        if (pausedUntil != null && now.isBefore(pausedUntil)) return -1;
        if (pending.isEmpty()) return igdbBatch(now);
        // Steam doğrulaması yavaştır (istek aralığı): IGDB partileri onu beklemeden, dönüşümlü ilerler.
        steamTurn = !steamTurn;
        return steamTurn ? igdbBatch(now) : steamStep(now);
    }

    private int igdbBatch(Instant now) {
        var rows = parents.candidates(now.minus(RECHECK), now.minus(UNKNOWN_RECHECK), before, BATCH);
        if (rows.isEmpty() && before != Long.MAX_VALUE) {
            before = Long.MAX_VALUE;   // tur bitti: baştan bak (hata nedeniyle işaretsiz kalanlar için)
            rows = parents.candidates(now.minus(RECHECK), now.minus(UNKNOWN_RECHECK), before, BATCH);
        }
        // Steam doğrulaması bekleyen (henüz işaretsiz) kayıtlar bir sonraki turda yeniden kuyruğa girmesin.
        java.util.Set<String> inFlight = pending.stream().map(item -> item.row().getExternalId()).collect(java.util.stream.Collectors.toSet());
        rows = rows.stream().filter(row -> !inFlight.contains(row.getExternalId())).toList();
        if (rows.isEmpty()) {
            if (!pending.isEmpty()) return steamStep(now);
            pausedUntil = now.plus(IDLE_INTERVAL);
            return -1;
        }
        Map<String, GameProvider.RelationEvidence> evidence = Map.of();
        try {
            if (provider.capabilities().contains(GameProvider.Capability.ITEM_TYPE)) {
                evidence = provider.relationEvidence(rows.stream().map(row -> row.getExternalId()).toList());
            }
        } catch (ProviderException error) {
            pause(error, now);
            return -1;
        }
        consecutiveFailures = 0;
        before = Long.parseLong(rows.getLast().getExternalId());   // yalnız başarılı sorgudan sonra ilerler (hata partiyi atlatmaz)
        List<Decision> immediate = new ArrayList<>();
        for (var row : rows) {
            var igdb = evidence.get(row.getExternalId());
            String igdbParent = igdb != null && igdb.parentState() == GameProvider.ParentState.LINKED ? igdb.parentExternalId() : null;
            boolean sampled = Long.parseLong(row.getExternalId()) % VERIFY_MODULUS == 0;
            if (igdbParent != null && !sampled) immediate.add(new Decision(row, igdbParent, null));
            else pending.add(new Pending(row, igdbParent));   // IGDB yok/belirsiz veya örneklem: Steam ile sorulur
        }
        if (!immediate.isEmpty()) transaction.executeWithoutResult(status -> apply(immediate, now));
        return rows.size();
    }

    private int steamStep(Instant now) {
        List<Pending> batch = pending.stream().limit(STEAM_BATCH).toList();
        Map<String, GameProvider.StoreType> found;
        try {
            found = provider.storeTypes(batch.stream().map(item -> item.row().getExternalId()).toList());
        } catch (ProviderException error) {
            if ("INVALID_PROVIDER_REQUEST".equals(error.code())) {
                batch.forEach(item -> pending.pollFirst());
            } else {
                pause(error, now);
            }
            return -1;
        }
        consecutiveFailures = 0;
        batch.forEach(item -> pending.pollFirst());
        List<Decision> decisions = batch.stream().map(item -> {
            GameProvider.StoreType type = found.get(item.row().getExternalId());
            return new Decision(item.row(), item.igdbParent(), type == null ? null : type.parentAppId());
        }).toList();
        transaction.executeWithoutResult(status -> apply(decisions, now));
        return decisions.size();
    }

    private void apply(List<Decision> decisions, Instant now) {
        states.lockProvider(PROVIDER_CODE).orElseThrow();
        games.lockCatalogGames(PROVIDER_CODE, decisions.stream().map(decision -> decision.row().getExternalId()).toList());
        Map<UUID, Game> loaded = games.findAllById(decisions.stream().map(decision -> decision.row().getGameId()).toList()).stream()
                .collect(Collectors.toMap(Game::getId, Function.identity()));
        Map<String, DlcParentRepository.ParentRow> parentRows = parentRows(decisions);
        Map<Outcome, Integer> batch = new EnumMap<>(Outcome.class);
        for (Decision decision : decisions) {
            Game game = loaded.get(decision.row().getGameId());
            if (!reviewable(game)) continue;
            Outcome outcome = decide(game, decision, parentRows);
            record(game, decision, outcome, parentRows, now);
            batch.merge(outcome, 1, Integer::sum);
            totals.merge(outcome, 1L, Long::sum);
        }
        LOG.info("DLC ana oyun incelemesi: sonuçlar={}", batch);
    }

    /** Hâlâ incelenebilir mi: aktif, harici, DLC ve henüz bağlı değil (kilit altında yeniden denetim). */
    private static boolean reviewable(Game game) {
        return game != null && game.getStatus() == GameStatus.ACTIVE && game.getOrigin() == GameOrigin.EXTERNAL
                && game.getGameType() == GameType.DLC && game.getParentGameId() == null;
    }

    /** Kararlarda geçen ana oyun AppID'lerinin katalogdaki oyunları. */
    private Map<String, DlcParentRepository.ParentRow> parentRows(List<Decision> decisions) {
        List<String> parentAppIds = decisions.stream().flatMap(decision -> java.util.stream.Stream.of(decision.igdbParent(), decision.steamParent()))
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (parentAppIds.isEmpty()) return Map.of();
        return parents.parentsByAppId(parentAppIds).stream()
                .collect(Collectors.toMap(DlcParentRepository.ParentRow::getAppId, Function.identity(), (first, second) -> first));
    }

    /** Sonucu uygular: bağlanabilirse ana oyunu yazar; her durumda DLC_PARENT gözlemini (kaynak ve durum) kaydeder. */
    private void record(Game game, Decision decision, Outcome outcome, Map<String, DlcParentRepository.ParentRow> parentRows, Instant now) {
        boolean linked = outcome == Outcome.LINKED || outcome == Outcome.LINKED_VIA_STEAM;
        if (linked) {
            String parentAppId = decision.igdbParent() != null ? decision.igdbParent() : decision.steamParent();
            game.setParentGameId(parentRows.get(parentAppId).getGameId());
            game.setUpdatedAt(now);
        }
        String source = decision.igdbParent() != null ? provider.detailSource() : provider.provider();
        MetadataDataState state = observationState(linked, outcome);
        provenance.recordObservation(game, MARKER, source, state, false);
    }

    private static MetadataDataState observationState(boolean linked, Outcome outcome) {
        if (linked) return MetadataDataState.AVAILABLE;
        return outcome == Outcome.CONFLICT ? MetadataDataState.INVALID : MetadataDataState.UNKNOWN;
    }

    /** Saf kanıt kuralları: ad, popülerlik veya AppID sırası kullanılmaz; çelişkide ilişki boş kalır. */
    static Outcome decide(Game child, Decision decision, Map<String, DlcParentRepository.ParentRow> parentRows) {
        if (decision.igdbParent() != null && decision.steamParent() != null && !decision.igdbParent().equals(decision.steamParent())) return Outcome.CONFLICT;
        String parentAppId = decision.igdbParent() != null ? decision.igdbParent() : decision.steamParent();
        if (parentAppId == null) return Outcome.NO_PARENT_EVIDENCE;
        var parent = parentRows.get(parentAppId);
        if (parent == null) return Outcome.PARENT_NOT_IN_CATALOG;
        if (parent.getGameId().equals(child.getId())) return Outcome.PARENT_NOT_MAIN_GAME;
        if (!"ACTIVE".equals(parent.getStatus()) || "UNKNOWN".equals(parent.getGameType())) return Outcome.PARENT_NOT_CLASSIFIED;
        if (!"MAIN_GAME".equals(parent.getGameType())) return Outcome.PARENT_NOT_MAIN_GAME;
        return decision.igdbParent() != null ? Outcome.LINKED : Outcome.LINKED_VIA_STEAM;
    }

    private void pause(ProviderException error, Instant now) {
        consecutiveFailures = Math.min(consecutiveFailures + 1, 6);
        Duration delay = retries.delay(error, Math.min(consecutiveFailures, 5));
        Duration pause = delay == null ? MAX_PAUSE : delay.multipliedBy(consecutiveFailures >= 2 ? 4L : 1L);
        if (pause.compareTo(Duration.ofMinutes(1)) < 0) pause = Duration.ofMinutes(1);
        if (pause.compareTo(MAX_PAUSE) > 0) pause = MAX_PAUSE;
        pausedUntil = now.plus(pause);
        LOG.warn("DLC ana oyun incelemesi duraklatıldı: kod={} ardışıkHata={} devam={}", error.code(), consecutiveFailures, pausedUntil);
    }
}
