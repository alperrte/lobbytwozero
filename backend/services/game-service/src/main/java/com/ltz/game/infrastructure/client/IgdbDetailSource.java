package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GameDetailSource;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.client.dto.response.IgdbResponses;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;

/**
 * IGDB detay kaynağı (GAME-17/23). Steam AppID, IGDB {@code external_games} kaydıyla tam eşleşme kanıtıyla bulunur:
 * kaynak kimliği sabit yazılmaz ({@code external_game_sources} adından çözülür, kullanımdan kalkan {@code category}
 * kullanılmaz); sıfır ya da birden çok aday otomatik eşlenmez. Twitch client-credentials token'ı bellekte tutulur;
 * istekler IGDB sınırının (4 istek/sn) altında tutulur. Yanıt güvenilmez girdidir; hata kodları URL, anahtar, token
 * veya ham yanıt taşımaz. Katalog sağlayıcısının yerine geçmez.
 */
public class IgdbDetailSource implements GameDetailSource {

    static final String SOURCE = "IGDB";
    private static final String GAME_FIELDS = "game_type,summary,first_release_date,involved_companies.developer,"
            + "involved_companies.publisher,involved_companies.company.name,"
            + "language_supports.language.locale,language_supports.language_support_type.name,cover.image_id";

    private final IgdbHttp http;
    private Long steamSourceId;

    public IgdbDetailSource(RestClient client, Clock clock, String clientId, String clientSecret,
                            String apiUrl, String tokenUrl, Duration minInterval) {
        this.http = new IgdbHttp(client, clock, clientId, clientSecret, apiUrl, tokenUrl, minInterval);
    }

    @Override public String source() { return SOURCE; }

    /** IGDB {@code game_type} kimliği istenir ve {@link IgdbDetailMapper#providerType} ile muhafazakâr eşlenir. */
    @Override public boolean reportsItemType() { return true; }

    @Override
    public synchronized GameProvider.Details details(String steamAppId) {
        if (steamAppId == null || !steamAppId.matches("[1-9]\\d{0,9}")) {
            throw new ProviderException("INVALID_PROVIDER_REQUEST");
        }
        long gameId = mappedGame(steamAppId);
        List<IgdbResponses.Game> games = query("/games", "fields " + GAME_FIELDS + "; where id = " + gameId + "; limit 1;",
                new TypeReference<List<IgdbResponses.Game>>() { });
        if (games.size() != 1 || games.getFirst() == null) {
            throw new ProviderException("PROVIDER_ITEM_NOT_FOUND");
        }
        return IgdbDetailMapper.toDetails(games.getFirst());
    }


    /** Toplu istek boyutu: external_games satırı sınırı (500) aşılmasın diye AppID başına en çok iki satıra yer bırakılır. */
    static final int MAX_BATCH = 250;
    /** Toplu detay boyutu: dil/şirket genişletmeleri yanıtı büyütür; IGDB yanıt gövdesi sınırı (1 MB) aşılmasın diye 100. */
    static final int MAX_BULK_DETAILS = 100;
    /** Ana oyun aramasında bir istekte sorgulanan en çok IGDB oyunu. */
    private static final int PARENT_CHUNK = 100;
    private static final int IGDB_PAGE_LIMIT = 500;
    private static final String EVIDENCE_FIELDS = "game_type,first_release_date,genres.slug,genres.name,platforms.slug,platforms.name,"
            + "game_modes.slug,game_modes.name,themes.slug,themes.name";
    private static final String RELATION_FIELDS = "game_type,parent_game";
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(IgdbDetailSource.class);

    /**
     * Toplu kanıt: iki istekle (external_games, games) en çok {@link #MAX_BATCH} AppID. Eşleme yalnız tam uid eşitliği ve tek
     * IGDB oyunu ile kabul edilir; yanıt sırası önemsizdir (oyun kimliğiyle eşlenir). Tür ve tarih bağımsızdır: geçersiz bir
     * tarih yalnız o oyunun tarihini düşürür, sayfayı bozmaz.
     */
    @Override
    public synchronized java.util.Map<String, GameProvider.ItemEvidence> itemEvidence(java.util.Collection<String> steamAppIds) {
        return bulk(steamAppIds, MAX_BATCH, this::evidenceByGame);
    }

    /**
     * Toplu detay: en çok {@link #MAX_BULK_DETAILS} AppID için iki istek (external_games, games). Eşleme tam uid eşitliği ve tek
     * IGDB oyunu ile kabul edilir. Geçersiz/aralık dışı veri yalnız o oyunu düşürür (sonuçta yer almaz), sayfayı bozmaz.
     */
    @Override
    public synchronized java.util.Map<String, GameProvider.Details> bulkDetails(java.util.Collection<String> steamAppIds) {
        return bulk(steamAppIds, MAX_BULK_DETAILS, this::detailsByGame);
    }

    /**
     * DLC ilişki kanıtı: AppID → IGDB {@code game_type} kimliği ve {@code parent_game}. Üst oyun IGDB kimliği, yalnız IGDB'nin
     * kendi Steam {@code external_games} kaydından Steam AppID'sine çevrilir (LTZ kimliğine eşit sayılmaz): tam bir Steam kaydı
     * varsa LINKED, hiç yoksa UNMAPPED, birden çoksa AMBIGUOUS. Eşleşmeyen veya belirsiz AppID sonuçta yer almaz.
     */
    @Override
    public synchronized java.util.Map<String, GameProvider.RelationEvidence> relationEvidence(java.util.Collection<String> steamAppIds) {
        return bulk(steamAppIds, MAX_BATCH, this::relationByGame);
    }

    /**
     * Üç toplu işlemin ortak akışı: AppID doğrulanır, her AppID tek bir IGDB oyununa eşlenir (belirsiz/çoklu eşleme atlanır),
     * {@code fetch} oyun kimlikleri için oyun başına değeri getirir ve sonuç AppID'ye geri eşlenir.
     */
    private <T> java.util.Map<String, T> bulk(java.util.Collection<String> steamAppIds, int max,
            java.util.function.Function<Set<Long>, java.util.Map<Long, T>> fetch) {
        List<String> ids = validIds(steamAppIds, max);
        java.util.Map<String, Long> gameOf = uniqueGames(ids);
        if (gameOf.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<Long, T> byGame = fetch.apply(new LinkedHashSet<>(gameOf.values()));
        java.util.Map<String, T> result = new java.util.LinkedHashMap<>();
        gameOf.forEach((id, game) -> {
            T value = byGame.get(game);
            if (value != null) {
                result.put(id, value);
            }
        });
        return result;
    }

    /** Tekrarsız AppID listesi; en çok {@code max} ve yalnız rakam. Boş girdi boş liste döner. */
    private static List<String> validIds(java.util.Collection<String> steamAppIds, int max) {
        List<String> ids = steamAppIds == null ? List.of() : steamAppIds.stream().distinct().toList();
        if (ids.size() > max || ids.stream().anyMatch(id -> id == null || !id.matches("[1-9]\\d{0,9}"))) {
            throw new ProviderException("INVALID_PROVIDER_REQUEST");
        }
        return ids;
    }

    /** AppID → IGDB oyunu; yalnız tek adaylı eşlemeler (birden çok aday otomatik birleştirilmez). Girdi sırası korunur. */
    private java.util.Map<String, Long> uniqueGames(List<String> ids) {
        java.util.Map<String, Long> unique = new java.util.LinkedHashMap<>();
        if (ids.isEmpty()) {
            return unique;
        }
        java.util.Map<String, Set<Long>> byUid = uidToGames(ids);
        for (String id : ids) {
            Set<Long> candidates = byUid.get(id);
            if (candidates != null && candidates.size() == 1) {
                unique.put(id, candidates.iterator().next());
            }
        }
        return unique;
    }

    private List<IgdbResponses.Game> games(Set<Long> gameIds, String fields) {
        String list = gameIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        return query("/games", "fields " + fields + "; where id = (" + list + "); limit " + IGDB_PAGE_LIMIT + ";",
                new TypeReference<List<IgdbResponses.Game>>() { });
    }

    private java.util.Map<Long, GameProvider.ItemEvidence> evidenceByGame(Set<Long> gameIds) {
        java.util.Map<Long, GameProvider.ItemEvidence> byGame = new java.util.HashMap<>();
        for (IgdbResponses.Game game : games(gameIds, EVIDENCE_FIELDS)) {
            if (game != null && game.id() != null) {
                IgdbEvidenceMapper.evidenceOf(game).ifPresent(evidence -> byGame.put(game.id(), evidence));
            }
        }
        return byGame;
    }

    private java.util.Map<Long, GameProvider.Details> detailsByGame(Set<Long> gameIds) {
        java.util.Map<Long, GameProvider.Details> byGame = new java.util.HashMap<>();
        for (IgdbResponses.Game game : games(gameIds, GAME_FIELDS)) {
            if (game != null && game.id() != null) {
                IgdbEvidenceMapper.detailsOf(game).ifPresent(details -> byGame.put(game.id(), details));
            }
        }
        return byGame;
    }

    private java.util.Map<Long, GameProvider.RelationEvidence> relationByGame(Set<Long> gameIds) {
        List<IgdbResponses.Game> games = games(gameIds, RELATION_FIELDS).stream().filter(game -> game != null && game.id() != null).toList();
        Set<Long> parents = new LinkedHashSet<>();
        games.forEach(game -> {
            if (game.parentGame() != null) {
                parents.add(game.parentGame());
            }
        });
        java.util.Map<Long, Set<String>> steamOfParent = steamUidsOf(parents);
        java.util.Map<Long, GameProvider.RelationEvidence> byGame = new java.util.HashMap<>();
        games.forEach(game -> byGame.put(game.id(), IgdbEvidenceMapper.relationOf(game, steamOfParent)));
        return byGame;
    }

    /** Steam AppID → IGDB oyun adayları (tam uid eşitliği). Kesilmiş olabilecek yanıt güvenilmez sayılır. */
    private java.util.Map<String, Set<Long>> uidToGames(List<String> ids) {
        String uids = ids.stream().map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(","));
        List<IgdbResponses.ExternalGame> rows = query("/external_games",
                "fields game,uid; where external_game_source = " + steamSource() + " & uid = (" + uids + "); limit " + IGDB_PAGE_LIMIT + ";",
                new TypeReference<List<IgdbResponses.ExternalGame>>() { });
        if (rows.size() >= IGDB_PAGE_LIMIT) {
            // Sınıra ulaşan yanıt kesilmiş olabilir; eksik satırla belirsizlik tespiti güvenilir olmaz.
            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        }
        java.util.Map<String, Set<Long>> byUid = new java.util.HashMap<>();
        Set<String> requested = Set.copyOf(ids);
        for (IgdbResponses.ExternalGame row : rows) {
            if (row != null && row.game() != null && row.uid() != null && requested.contains(row.uid())) {
                byUid.computeIfAbsent(row.uid(), key -> new LinkedHashSet<>()).add(row.game());
            }
        }
        return byUid;
    }

    /** IGDB oyun kimliği → o oyunun IGDB'de kayıtlı Steam AppID'leri (parçalı sorgu; kesilen yanıt hata sayılır). */
    private java.util.Map<Long, Set<String>> steamUidsOf(Set<Long> igdbGameIds) {
        java.util.Map<Long, Set<String>> result = new java.util.HashMap<>();
        List<Long> all = List.copyOf(igdbGameIds);
        for (int from = 0; from < all.size(); from += PARENT_CHUNK) {
            String list = all.subList(from, Math.min(all.size(), from + PARENT_CHUNK)).stream().map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));
            List<IgdbResponses.ExternalGame> rows = query("/external_games",
                    "fields game,uid; where external_game_source = " + steamSource() + " & game = (" + list + "); limit " + IGDB_PAGE_LIMIT + ";",
                    new TypeReference<List<IgdbResponses.ExternalGame>>() { });
            if (rows.size() >= IGDB_PAGE_LIMIT) {
                throw new ProviderException("INVALID_PROVIDER_RESPONSE");
            }
            for (IgdbResponses.ExternalGame row : rows) {
                if (row != null && row.game() != null && row.uid() != null && row.uid().matches("[1-9]\\d{0,9}")) {
                    result.computeIfAbsent(row.game(), key -> new LinkedHashSet<>()).add(row.uid());
                }
            }
        }
        return result;
    }

    // ---- Eşleme: Steam AppID -> IGDB oyunu (tam dış kimlik kanıtı)
    private long mappedGame(String steamAppId) {
        String body = "fields game,uid; where external_game_source = " + steamSource() + " & uid = \"" + steamAppId + "\"; limit 10;";
        List<IgdbResponses.ExternalGame> rows = query("/external_games", body, new TypeReference<List<IgdbResponses.ExternalGame>>() { });
        Set<Long> games = new LinkedHashSet<>();
        for (IgdbResponses.ExternalGame row : rows) {
            if (row != null && row.game() != null && steamAppId.equals(row.uid())) {
                games.add(row.game());
            }
        }
        if (games.isEmpty()) {
            throw new ProviderException("PROVIDER_ITEM_NOT_FOUND");
        }
        if (games.size() > 1) {
            // Birden çok aday (edition/remaster vb.) otomatik birleştirilmez.
            throw new ProviderException("AMBIGUOUS_PROVIDER_MAPPING");
        }
        return games.iterator().next();
    }

    private long steamSource() {
        if (steamSourceId == null) {
            List<IgdbResponses.Source> rows = query("/external_game_sources", "fields name; where name = \"Steam\"; limit 5;",
                    new TypeReference<List<IgdbResponses.Source>>() { });
            List<Long> ids = rows.stream().filter(row -> row != null && row.id() != null && "steam".equalsIgnoreCase(row.name()))
                    .map(IgdbResponses.Source::id).distinct().toList();
            if (ids.size() != 1) {
                throw new ProviderException(ids.isEmpty() ? "INVALID_PROVIDER_RESPONSE" : "AMBIGUOUS_PROVIDER_MAPPING");
            }
            steamSourceId = ids.getFirst();
        }
        return steamSourceId;
    }

    private <T> List<T> query(String path, String body, TypeReference<List<T>> type) {
        return http.query(path, body, type);
    }
}
