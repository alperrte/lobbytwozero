package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.application.port.StoreDetailSource;
import com.ltz.game.application.service.ProviderCoverPolicy;
import com.ltz.game.domain.enums.LanguageSupportStatus;
import com.ltz.game.domain.exception.ProviderException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongConsumer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steam mağaza API'si ({@code store.steampowered.com/api/appdetails}), herkese açık ve anahtarsız. İstekler tek tek ve aralıklıdır
 * (varsayılan 1,7 sn; Steam yaklaşık 5 dakikada 200 isteğe izin verir); 429/403 hız sınırı sayılır ve uzun beklemeye alınır.
 * Yanıt güvenilmez girdidir: boyut sınırlı, alanlar doğrulanır, bilinmeyen dil adı atlanır (kod uydurulmaz).
 */
public class SteamStoreClient implements StoreDetailSource {
    private static final long MAX_BODY = 524_288;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final java.util.regex.Pattern APP_ID = java.util.regex.Pattern.compile("[1-9]\\d{0,9}");
    /** Steam dil adı → katalog dil kodu (IGDB ile aynı küçük harfli BCP47 biçimi). */
    private static final Map<String, String> LANGUAGE_CODES = Map.ofEntries(
            Map.entry("english", "en-us"), Map.entry("german", "de-de"), Map.entry("french", "fr-fr"),
            Map.entry("spanish - spain", "es-es"), Map.entry("spanish - latin america", "es-mx"), Map.entry("italian", "it-it"),
            Map.entry("russian", "ru-ru"), Map.entry("polish", "pl-pl"), Map.entry("japanese", "ja-jp"),
            Map.entry("simplified chinese", "zh-cn"), Map.entry("traditional chinese", "zh-tw"), Map.entry("korean", "ko-kr"),
            Map.entry("czech", "cs-cz"), Map.entry("portuguese - brazil", "pt-br"), Map.entry("portuguese - portugal", "pt-pt"),
            Map.entry("dutch", "nl-nl"), Map.entry("hungarian", "hu-hu"), Map.entry("swedish", "sv-se"), Map.entry("turkish", "tr-tr"),
            Map.entry("danish", "da-dk"), Map.entry("finnish", "fi-fi"), Map.entry("norwegian", "nb-no"), Map.entry("ukrainian", "uk-ua"),
            Map.entry("thai", "th-th"), Map.entry("vietnamese", "vi-vn"), Map.entry("arabic", "ar"), Map.entry("hebrew", "he-il"),
            Map.entry("greek", "el-gr"), Map.entry("romanian", "ro-ro"), Map.entry("bulgarian", "bg-bg"), Map.entry("indonesian", "id-id"));

    private final RestClient client;
    private final Duration minInterval;
    private final LongConsumer sleeper;
    private long lastCall;

    public SteamStoreClient(RestClient client, Duration minInterval) {
        this(client, minInterval, SteamStoreClient::sleepMillis);
    }

    SteamStoreClient(RestClient client, Duration minInterval, LongConsumer sleeper) {
        this.client = client;
        this.minInterval = minInterval;
        this.sleeper = sleeper;
        this.lastCall = System.nanoTime() - minInterval.toNanos();
    }

    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new ProviderException("PROVIDER_TRANSPORT_ERROR");
        }
    }

    @Override
    public synchronized Optional<StoreDetails> details(String appId) {
        if (appId == null || !APP_ID.matcher(appId).matches()) throw new ProviderException("INVALID_PROVIDER_REQUEST");
        pace();
        byte[] body;
        try {
            body = client.get().uri("/api/appdetails?appids={id}&cc=us&l=english", appId).exchange((request, response) -> {
                int status = response.getStatusCode().value();
                if (status == 429 || status == 403) throw new ProviderException("RATE_LIMITED", Duration.ofMinutes(5));
                if (status >= 500) throw new ProviderException("PROVIDER_UNAVAILABLE");
                if (status != 200) throw new ProviderException("PROVIDER_REQUEST_REJECTED");
                byte[] bytes = response.getBody().readNBytes((int) MAX_BODY + 1);
                if (bytes.length > MAX_BODY) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
                return bytes;
            });
        } catch (RestClientException _) {
            throw new ProviderException("PROVIDER_TRANSPORT_ERROR");
        }
        return parse(appId, body);
    }

    private void pace() {
        long waitMillis = minInterval.toMillis() - (System.nanoTime() - lastCall) / 1_000_000;
        if (waitMillis > 0) sleeper.accept(waitMillis);
        lastCall = System.nanoTime();
    }

    static Optional<StoreDetails> parse(String appId, byte[] body) {
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (JacksonException _) {
            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        }
        JsonNode entry = root == null ? null : root.get(appId);
        if (entry == null || !entry.isObject() || !entry.has("success")) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        if (!entry.get("success").asBoolean(false)) return Optional.empty();
        JsonNode data = entry.get("data");
        if (data == null || !data.isObject()) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        return Optional.of(new StoreDetails(text(data, "type"), ProviderCoverPolicy.steamHeader(text(data, "header_image")),
                names(data.get("developers")), names(data.get("publishers")), languages(text(data, "supported_languages")), fullGameAppId(data)));
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asString() : null;
    }

    /** DLC'nin ana oyun AppID'si; yoksa veya biçimi geçersizse null. */
    private static String fullGameAppId(JsonNode data) {
        JsonNode full = data.get("fullgame");
        if (full == null || !full.isObject() || !full.hasNonNull("appid")) return null;
        String appId = full.get("appid").asString();
        return APP_ID.matcher(appId).matches() ? appId : null;
    }

    private static Set<String> names(JsonNode array) {
        Set<String> result = new LinkedHashSet<>();
        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                String name = item.isString() ? item.asString().strip() : "";
                if (!name.isEmpty() && name.length() <= 256 && result.size() < 50) result.add(name);
            }
        }
        return result;
    }

    /**
     * {@code supported_languages}: virgülle ayrılmış dil adları; tam ses desteği olan dil {@code <strong>*</strong>} ile işaretlidir ve
     * metin sonunda "languages with full audio support" notu gelir. Bilinmeyen dil adı atlanır.
     */
    static List<GameProvider.LanguageSupport> languages(String html) {
        if (html == null || html.length() > 4096) return List.of();
        String list = html.split("<br", 2)[0];
        Map<String, GameProvider.LanguageSupport> byCode = new LinkedHashMap<>();
        for (String part : list.split(",")) {
            boolean audio = part.contains("*");
            String name = part.replaceAll("<[^>]*>", "").replace("*", "").strip().toLowerCase(Locale.ROOT);
            String code = LANGUAGE_CODES.get(name);
            if (code == null || byCode.containsKey(code)) continue;
            byCode.put(code, new GameProvider.LanguageSupport(code, LanguageSupportStatus.SUPPORTED,
                    audio ? LanguageSupportStatus.SUPPORTED : LanguageSupportStatus.UNKNOWN, LanguageSupportStatus.UNKNOWN));
        }
        return new ArrayList<>(byCode.values());
    }
}
