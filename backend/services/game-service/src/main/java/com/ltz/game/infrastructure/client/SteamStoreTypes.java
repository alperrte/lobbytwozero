package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.exception.ProviderException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code IStoreBrowseService/GetItems} yanıtından mağaza türü ve tür-türevi alanları ({@link GameProvider.StoreType}) ile
 * {@code IStoreService/GetTagList} yanıtından etiket adlarını ayrıştırır. Yanıt güvenilmez girdi sayılır; Steam'in vermediği alan uydurulmaz.
 */
final class SteamStoreTypes {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Etiket/kategori sayısı üst sınırı (yanıt şişirmeye karşı). */
    private static final int MAX_IDS = 100;
    /** Makul çıkış tarihi aralığı (saniye): 1990-01-01 – 2100-01-01 (UTC). */
    private static final long MIN_RELEASE = 631_152_000L;
    private static final long MAX_RELEASE = 4_102_444_800L;
    /** Steam platform anahtarı → sağlayıcıdan bağımsız ad (Steam Linux'u {@code steamos_linux} diye bildirir). */
    private static final Map<String, String> PLATFORMS = Map.of("windows", "windows", "mac", "mac", "steamos_linux", "linux");

    private SteamStoreTypes() { }

    /** Mağaza türü isteği: çıkış bilgisi, en çok 20 etiket ve platformlar (tek çağrı). */
    static String input(List<String> ids) {
        return SteamStoreBrowse.rawInput(ids, "english", "{\"include_release\":true,\"include_tag_count\":20,\"include_platforms\":true}");
    }

    /** Steam'in tanıdığı ({@code success=1}) her uygulama için bir {@link GameProvider.StoreType}; tanımadığı ya da türsüz uygulama sonuçta yer almaz. */
    static Map<String, GameProvider.StoreType> parse(List<String> requested, byte[] body) {
        Map<String, GameProvider.StoreType> result = new LinkedHashMap<>();
        for (JsonNode item : SteamStoreBrowse.storeItemNodes(body)) {
            String appId = SteamStoreBrowse.appId(item, requested);
            if (appId == null || item.path("success").asInt(0) != 1 || !item.hasNonNull("type")) {
                continue;
            }
            JsonNode release = item.path("release");
            result.put(appId, new GameProvider.StoreType(item.get("type").asInt(), item.path("visible").asBoolean(true), releasedOn(release),
                    parent(item.path("related_items")), ids(item.path("tagids")), ids(item.path("categories").path("supported_player_categoryids")),
                    platforms(item.path("platforms"))));
        }
        return result;
    }

    /** Yalnız oyun "yakında" değilse ve tarih makul aralıktaysa; aksi null (tahmini/boş tarih yazılmaz). */
    private static LocalDate releasedOn(JsonNode release) {
        if (!release.isObject() || release.path("is_coming_soon").asBoolean(false) || !release.hasNonNull("steam_release_date")) {
            return null;
        }
        long seconds = release.get("steam_release_date").asLong(0);
        if (seconds < MIN_RELEASE || seconds > MAX_RELEASE || Instant.ofEpochSecond(seconds).isAfter(Instant.now())) {
            return null;
        }
        return Instant.ofEpochSecond(seconds).atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static String parent(JsonNode related) {
        if (!related.hasNonNull("parent_appid")) {
            return null;
        }
        String id = related.get("parent_appid").asString();
        return id.matches("[1-9]\\d{0,9}") ? id : null;
    }

    private static Set<Integer> ids(JsonNode array) {
        Set<Integer> result = new LinkedHashSet<>();
        if (array.isArray()) {
            for (JsonNode value : array) {
                if (result.size() < MAX_IDS && value.canConvertToInt() && value.asInt() > 0) {
                    result.add(value.asInt());
                }
            }
        }
        return result;
    }

    private static Set<String> platforms(JsonNode object) {
        Set<String> result = new LinkedHashSet<>();
        if (object.isObject()) {
            PLATFORMS.forEach((key, name) -> {
                if (object.path(key).asBoolean(false)) {
                    result.add(name);
                }
            });
        }
        return result;
    }

    /** {@code IStoreService/GetTagList}: etiket kimliği → ad; boş/uzun adlar ve geçersiz kimlikler atlanır. */
    static Map<Integer, String> parseTagNames(byte[] body) {
        JsonNode tags;
        try {
            JsonNode root = JSON.readTree(body);
            tags = root == null ? null : root.path("response").path("tags");
        } catch (JacksonException _) {
            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        }
        if (tags == null || !tags.isArray()) {
            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        }
        Map<Integer, String> result = new LinkedHashMap<>();
        for (JsonNode tag : tags) {
            String name = tag.hasNonNull("name") ? tag.get("name").asString().strip() : "";
            if (tag.path("tagid").canConvertToInt() && tag.path("tagid").asInt() > 0 && !name.isEmpty() && name.length() <= 128) {
                result.put(tag.get("tagid").asInt(), name);
            }
        }
        return result;
    }
}
