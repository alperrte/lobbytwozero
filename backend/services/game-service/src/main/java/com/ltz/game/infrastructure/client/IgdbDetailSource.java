package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GameDetailSource;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.client.dto.response.IgdbResponses;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * IGDB detay kaynağı (GAME-17/23). Steam AppID, IGDB {@code external_games} kaydıyla tam eşleşme kanıtıyla bulunur:
 * kaynak kimliği sabit yazılmaz ({@code external_game_sources} adından çözülür, kullanımdan kalkan {@code category}
 * kullanılmaz); sıfır ya da birden çok aday otomatik eşlenmez. Twitch client-credentials token'ı bellekte tutulur;
 * istekler IGDB sınırının (4 istek/sn) altında tutulur. Yanıt güvenilmez girdidir; hata kodları URL, anahtar, token
 * veya ham yanıt taşımaz. Katalog sağlayıcısının yerine geçmez.
 */
public class IgdbDetailSource implements GameDetailSource {

    static final String SOURCE = "IGDB";
    private static final long MAX_BODY = 1_048_576;
    private static final Duration TOKEN_MARGIN = Duration.ofMinutes(5);
    private static final String GAME_FIELDS = "game_type,summary,first_release_date,involved_companies.developer,"
            + "involved_companies.publisher,involved_companies.company.name,"
            + "language_supports.language.locale,language_supports.language_support_type.name,cover.image_id";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private record CachedToken(String value, Instant refreshAt) { }

    private final RestClient client;
    private final Clock clock;
    private final String clientId;
    private final String clientSecret;
    private final String apiUrl;
    private final String tokenUrl;
    private final Duration minInterval;
    private CachedToken token;
    private Long steamSourceId;
    private long lastCall;

    public IgdbDetailSource(RestClient client, Clock clock, String clientId, String clientSecret,
                            String apiUrl, String tokenUrl, Duration minInterval) {
        this.client = client;
        this.clock = clock;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.apiUrl = apiUrl;
        this.tokenUrl = tokenUrl;
        this.minInterval = minInterval;
        this.lastCall = System.nanoTime() - minInterval.toNanos();
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
    private static final int IGDB_PAGE_LIMIT = 500;

    /**
     * Toplu kanıt: iki istekle (external_games, games) en çok {@link #MAX_BATCH} AppID. Eşleme yalnız tam uid eşitliği ve tek
     * IGDB oyunu ile kabul edilir; yanıt sırası önemsizdir (oyun kimliğiyle eşlenir). Tür ve tarih bağımsızdır: geçersiz bir
     * tarih yalnız o oyunun tarihini düşürür, sayfayı bozmaz.
     */
    @Override
    public synchronized java.util.Map<String, GameProvider.ItemEvidence> itemEvidence(java.util.Collection<String> steamAppIds) {
        List<String> ids = steamAppIds == null ? List.of() : steamAppIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        if (ids.size() > MAX_BATCH || ids.stream().anyMatch(id -> id == null || !id.matches("[1-9]\\d{0,9}"))) {
            throw new ProviderException("INVALID_PROVIDER_REQUEST");
        }
        java.util.Map<String, Set<Long>> byUid = uidToGames(ids);
        Set<Long> gameIds = new LinkedHashSet<>();
        for (String id : ids) {
            Set<Long> candidates = byUid.get(id);
            if (candidates != null && candidates.size() == 1) {
                gameIds.add(candidates.iterator().next());
            }
        }
        if (gameIds.isEmpty()) {
            return java.util.Map.of();
        }
        String list = gameIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        List<IgdbResponses.Game> games = query("/games",
                "fields game_type,first_release_date,genres.slug,genres.name,platforms.slug,platforms.name,game_modes.slug,game_modes.name,themes.slug,themes.name; where id = (" + list + "); limit " + IGDB_PAGE_LIMIT + ";",
                new TypeReference<List<IgdbResponses.Game>>() { });
        java.util.Map<Long, GameProvider.ItemEvidence> evidenceByGame = new java.util.HashMap<>();
        for (IgdbResponses.Game game : games) {
            if (game == null || game.id() == null) {
                continue;
            }
            String type = IgdbDetailMapper.providerType(game.gameType());
            java.time.LocalDate date = IgdbDetailMapper.lenientReleaseDate(game.firstReleaseDate());
            List<GameProvider.TermEvidence> genres = IgdbDetailMapper.termEvidence(game.genres());
            List<GameProvider.TermEvidence> platforms = IgdbDetailMapper.termEvidence(game.platforms());
            // Oyun modları → kategori (Tek oyunculu, Çok oyunculu, Co-op...), temalar → etiket. IGDB keywords kullanıcı etiketidir
            // (binlerce, gürültülü) ve bilerek eşlenmez; tür (genre), kategori ve etiket kavramları ayrı tutulur.
            List<GameProvider.TermEvidence> categories = IgdbDetailMapper.termEvidence(game.gameModes());
            List<GameProvider.TermEvidence> tags = IgdbDetailMapper.termEvidence(game.themes());
            if (type != null || date != null || !genres.isEmpty() || !platforms.isEmpty() || !categories.isEmpty() || !tags.isEmpty()) {
                evidenceByGame.put(game.id(), new GameProvider.ItemEvidence(type, date, genres, platforms, categories, tags));
            }
        }
        java.util.Map<String, GameProvider.ItemEvidence> result = new java.util.LinkedHashMap<>();
        for (String id : ids) {
            Set<Long> candidates = byUid.get(id);
            if (candidates != null && candidates.size() == 1 && evidenceByGame.containsKey(candidates.iterator().next())) {
                result.put(id, evidenceByGame.get(candidates.iterator().next()));
            }
        }
        return result;
    }

    /** Toplu detay boyutu: dil/şirket genişletmeleri yanıtı büyütür; IGDB yanıt gövdesi sınırı (1 MB) aşılmasın diye 100. */
    static final int MAX_BULK_DETAILS = 100;

    /**
     * Toplu detay: en çok {@link #MAX_BULK_DETAILS} AppID için iki istek (external_games, games). Eşleme tam uid eşitliği ve tek
     * IGDB oyunu ile kabul edilir. Geçersiz/aralık dışı veri yalnız o oyunu düşürür (sonuçta yer almaz), sayfayı bozmaz.
     */
    @Override
    public synchronized java.util.Map<String, GameProvider.Details> bulkDetails(java.util.Collection<String> steamAppIds) {
        List<String> ids = steamAppIds == null ? List.of() : steamAppIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        if (ids.size() > MAX_BULK_DETAILS || ids.stream().anyMatch(id -> id == null || !id.matches("[1-9]\\d{0,9}"))) {
            throw new ProviderException("INVALID_PROVIDER_REQUEST");
        }
        java.util.Map<String, Set<Long>> byUid = uidToGames(ids);
        Set<Long> gameIds = new LinkedHashSet<>();
        for (String id : ids) {
            Set<Long> candidates = byUid.get(id);
            if (candidates != null && candidates.size() == 1) {
                gameIds.add(candidates.iterator().next());
            }
        }
        if (gameIds.isEmpty()) {
            return java.util.Map.of();
        }
        String list = gameIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        List<IgdbResponses.Game> games = query("/games", "fields " + GAME_FIELDS + "; where id = (" + list + "); limit " + IGDB_PAGE_LIMIT + ";",
                new TypeReference<List<IgdbResponses.Game>>() { });
        java.util.Map<Long, GameProvider.Details> detailsByGame = new java.util.HashMap<>();
        for (IgdbResponses.Game game : games) {
            if (game == null || game.id() == null) {
                continue;
            }
            try {
                detailsByGame.put(game.id(), IgdbDetailMapper.toDetails(game));
            } catch (ProviderException _) {
                // Bu oyunun verisi geçersiz: yalnız o oyun kanıtsız kalır.
            }
        }
        java.util.Map<String, GameProvider.Details> result = new java.util.LinkedHashMap<>();
        for (String id : ids) {
            Set<Long> candidates = byUid.get(id);
            if (candidates != null && candidates.size() == 1 && detailsByGame.containsKey(candidates.iterator().next())) {
                result.put(id, detailsByGame.get(candidates.iterator().next()));
            }
        }
        return result;
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

    /** Ana oyun aramasında bir istekte sorgulanan en çok IGDB oyunu. */
    private static final int PARENT_CHUNK = 100;

    /**
     * DLC ilişki kanıtı: AppID → IGDB {@code game_type} kimliği ve {@code parent_game}. Üst oyun IGDB kimliği, yalnız IGDB'nin
     * kendi Steam {@code external_games} kaydından Steam AppID'sine çevrilir (LTZ kimliğine eşit sayılmaz): tam bir Steam kaydı
     * varsa LINKED, hiç yoksa UNMAPPED, birden çoksa AMBIGUOUS. Eşleşmeyen veya belirsiz AppID sonuçta yer almaz.
     */
    @Override
    public synchronized java.util.Map<String, GameProvider.RelationEvidence> relationEvidence(java.util.Collection<String> steamAppIds) {
        List<String> ids = steamAppIds == null ? List.of() : steamAppIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        if (ids.size() > MAX_BATCH || ids.stream().anyMatch(id -> id == null || !id.matches("[1-9]\\d{0,9}"))) {
            throw new ProviderException("INVALID_PROVIDER_REQUEST");
        }
        java.util.Map<String, Set<Long>> byUid = uidToGames(ids);
        Set<Long> gameIds = new LinkedHashSet<>();
        for (String id : ids) {
            Set<Long> candidates = byUid.get(id);
            if (candidates != null && candidates.size() == 1) {
                gameIds.add(candidates.iterator().next());
            }
        }
        if (gameIds.isEmpty()) {
            return java.util.Map.of();
        }
        String list = gameIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        List<IgdbResponses.Game> games = query("/games", "fields game_type,parent_game; where id = (" + list + "); limit " + IGDB_PAGE_LIMIT + ";",
                new TypeReference<List<IgdbResponses.Game>>() { });
        java.util.Map<Long, IgdbResponses.Game> byId = new java.util.HashMap<>();
        Set<Long> parents = new LinkedHashSet<>();
        for (IgdbResponses.Game game : games) {
            if (game != null && game.id() != null) {
                byId.put(game.id(), game);
                if (game.parentGame() != null) parents.add(game.parentGame());
            }
        }
        java.util.Map<Long, Set<String>> steamOfParent = steamUidsOf(parents);
        java.util.Map<String, GameProvider.RelationEvidence> result = new java.util.LinkedHashMap<>();
        for (String id : ids) {
            Set<Long> candidates = byUid.get(id);
            IgdbResponses.Game game = candidates != null && candidates.size() == 1 ? byId.get(candidates.iterator().next()) : null;
            if (game == null) {
                continue;
            }
            Integer type = game.gameType() != null && game.gameType().intValue() == game.gameType() ? game.gameType().intValue() : null;
            if (game.parentGame() == null) {
                result.put(id, new GameProvider.RelationEvidence(type, GameProvider.ParentState.NONE, null));
                continue;
            }
            Set<String> uids = steamOfParent.getOrDefault(game.parentGame(), Set.of());
            result.put(id, uids.size() == 1
                    ? new GameProvider.RelationEvidence(type, GameProvider.ParentState.LINKED, uids.iterator().next())
                    : new GameProvider.RelationEvidence(type, uids.isEmpty() ? GameProvider.ParentState.UNMAPPED : GameProvider.ParentState.AMBIGUOUS, null));
        }
        return result;
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

    // ---- HTTP
    private <T> List<T> query(String path, String body, TypeReference<List<T>> type) {
        String bearer = bearer();
        pace();
        byte[] response = exchange(path, body, bearer);
        try {
            List<T> rows = JSON.readValue(response, type);
            if (rows == null) {
                throw new ProviderException("INVALID_PROVIDER_RESPONSE");
            }
            return rows;
        } catch (JacksonException _) {
            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        }
    }

    private byte[] exchange(String path, String body, String bearer) {
        try {
            return client.post().uri(apiUrl + path)
                    .header("Client-ID", clientId)
                    .header("Authorization", "Bearer " + bearer)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(body)
                    .exchange((request, response) -> {
                        validateStatus(response.getStatusCode().value(), response.getHeaders().getFirst("Retry-After"));
                        return readBounded(response.getBody().readNBytes((int) MAX_BODY + 1));
                    });
        } catch (RestClientException _) {
            throw new ProviderException("PROVIDER_TRANSPORT_ERROR");
        }
    }

    private static byte[] readBounded(byte[] bytes) {
        if (bytes.length > MAX_BODY) {
            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        }
        return bytes;
    }

    private void validateStatus(int status, String retryAfter) {
        if (status == 200) {
            return;
        }
        if (status == 401 || status == 403) {
            token = null; // geçersiz/iptal token yeniden alınır; aynı çağrıda yeniden deneme yok
            throw new ProviderException("PROVIDER_ACCESS_DENIED");
        }
        if (status == 429) {
            throw new ProviderException("RATE_LIMITED", seconds(retryAfter));
        }
        throw new ProviderException(status >= 500 ? "PROVIDER_UNAVAILABLE" : "INVALID_PROVIDER_RESPONSE");
    }

    private static Duration seconds(String value) {
        if (value == null || !value.matches("\\d{1,7}")) {
            return null;
        }
        return Duration.ofSeconds(Long.parseLong(value));
    }

    private void pace() {
        long deadline = lastCall + minInterval.toNanos();
        long wait = deadline - System.nanoTime();
        while (wait > 0 && !Thread.currentThread().isInterrupted()) {
            LockSupport.parkNanos(wait);
            wait = deadline - System.nanoTime();
        }
        if (Thread.currentThread().isInterrupted()) {
            // Kesinti bayrağı korunur; çağıran bunu geçici bir taşıma hatası olarak görür.
            throw new ProviderException("PROVIDER_TRANSPORT_ERROR");
        }
        lastCall = System.nanoTime();
    }

    // ---- Twitch OAuth (client credentials); gizli anahtar URL'de değil, form gövdesinde gider
    private String bearer() {
        Instant now = clock.instant();
        if (token != null && now.isBefore(token.refreshAt())) {
            return token.value();
        }
        IgdbResponses.Token response = fetchToken();
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()
                || response.expiresIn() == null || response.expiresIn() <= 0) {
            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        }
        Duration lifetime = Duration.ofSeconds(response.expiresIn());
        Duration margin = lifetime.compareTo(TOKEN_MARGIN.multipliedBy(2)) > 0 ? TOKEN_MARGIN : lifetime.dividedBy(2);
        token = new CachedToken(response.accessToken(), now.plus(lifetime).minus(margin));
        return response.accessToken();
    }

    private IgdbResponses.Token fetchToken() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "client_credentials");
        try {
            byte[] body = client.post().uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 200) {
                            return readBounded(response.getBody().readNBytes((int) MAX_BODY + 1));
                        }
                        throw new ProviderException(status >= 500 ? "PROVIDER_UNAVAILABLE" : "PROVIDER_ACCESS_DENIED");
                    });
            return JSON.readValue(body, IgdbResponses.Token.class);
        } catch (RestClientException _) {
            throw new ProviderException("PROVIDER_TRANSPORT_ERROR");
        } catch (JacksonException _) {
            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        }
    }
}
