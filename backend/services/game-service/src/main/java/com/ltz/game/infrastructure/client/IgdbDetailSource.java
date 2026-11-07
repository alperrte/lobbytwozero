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
    private static final String GAME_FIELDS = "summary,first_release_date,involved_companies.developer,"
            + "involved_companies.publisher,involved_companies.company.name,"
            + "language_supports.language.locale,language_supports.language_support_type.name";
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
