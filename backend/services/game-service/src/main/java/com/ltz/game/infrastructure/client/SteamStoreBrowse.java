package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.application.service.ProviderCoverPolicy;
import com.ltz.game.domain.enums.LanguageSupportStatus;
import com.ltz.game.domain.exception.ProviderException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resmî {@code IStoreBrowseService/GetItems} için durumsuz yardımcılar: istek gövdesini yalnız doğrulanmış değerlerden kurar ve yanıtı
 * güvenilmez girdi sayarak ayrıştırır. HTTP, hız sınırı ve anahtar {@link SteamGameProvider}'dadır. Sağlayıcının vermediği alan uydurulmaz.
 */
final class SteamStoreBrowse {
    /** Bir istekte en çok bu kadar AppID (adres uzunluğu sınırı için). */
    static final int MAX_BATCH = 100;
    /** Yanıt gövdesi üst sınırı (bayt). */
    static final int MAX_BODY = 8_388_608;
    private static final String ASSET_BASE = "https://shared.fastly.steamstatic.com/store_item_assets/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Katalog dil kodu → Steam dil adı (yalnız ihtiyaç duyulan diller; diğerleri için gereksiz istek atılmaz). */
    private static final Map<String, String> STEAM_LANGUAGE_NAMES = Map.of("en", "english", "tr", "turkish");
    /**
     * Steam {@code ELanguage} numarası → katalog dil kodu. Eşleme, aynı oyunların mağaza sayfasındaki dil adlarıyla çapraz doğrulanarak
     * çıkarılmıştır (resmî sıralamadan farklı olabilir). Ayırt edilemeyen (20, 23) ve bilinmeyen numaralar bilerek ATLANIR: yanlış dil
     * etiketi yazmaktansa dil eksik kalır.
     */
    private static final Map<Integer, String> LANGUAGE_CODES = Map.ofEntries(
            Map.entry(0, "en-us"), Map.entry(1, "de-de"), Map.entry(2, "fr-fr"), Map.entry(3, "it-it"),
            Map.entry(4, "ko-kr"), Map.entry(5, "es-es"), Map.entry(6, "zh-cn"), Map.entry(7, "zh-tw"),
            Map.entry(8, "ru-ru"), Map.entry(9, "th-th"), Map.entry(10, "ja-jp"), Map.entry(11, "pt-pt"),
            Map.entry(12, "pl-pl"), Map.entry(13, "da-dk"), Map.entry(14, "nl-nl"), Map.entry(15, "fi-fi"),
            Map.entry(16, "nb-no"), Map.entry(17, "sv-se"), Map.entry(18, "hu-hu"), Map.entry(19, "cs-cz"),
            Map.entry(21, "tr-tr"), Map.entry(22, "pt-br"), Map.entry(24, "el-gr"), Map.entry(25, "ar"),
            Map.entry(26, "uk-ua"), Map.entry(27, "es-mx"), Map.entry(28, "vi-vn"), Map.entry(30, "id-id"));

    private SteamStoreBrowse() { }

    /** Tekrarsız AppID listesi; en çok {@link #MAX_BATCH} ve yalnız rakam (aksi INVALID_PROVIDER_REQUEST). Boş girdi boş liste döner. */
    static List<String> validIds(Collection<String> externalIds) {
        List<String> ids = externalIds == null ? List.of() : externalIds.stream().distinct().toList();
        if (ids.size() > MAX_BATCH || ids.stream().anyMatch(id -> id == null || !id.matches("[1-9]\\d{0,9}"))) {
            throw new ProviderException("INVALID_PROVIDER_REQUEST");
        }
        return ids;
    }

    /** Katalog dil kodunun Steam karşılığı; desteklenmeyen dil için istek hiç atılmaz. */
    static String steamLanguage(String language) {
        String name = language == null ? null : STEAM_LANGUAGE_NAMES.get(language);
        if (name == null) {
            throw new ProviderException("INVALID_PROVIDER_REQUEST");
        }
        return name;
    }

    /** Başlık görseli, geliştirici/yayıncı ve dil desteği isteği (İngilizce bağlam). */
    static String storeItemsInput(List<String> ids) {
        return input(ids, "english", "{\"include_assets\":true,\"include_basic_info\":true,\"include_supported_languages\":true}");
    }

    /** Yalnız kısa açıklama isteği, verilen Steam dilinde. */
    static String shortDescriptionInput(List<String> ids, String steamLanguage) {
        return input(ids, steamLanguage, "{\"include_basic_info\":true}");
    }

    /** Yalnız çıkış bilgisi isteği (erken erişim, ücretsiz ve yakında bayrakları aynı yanıttan gelir). */
    static String releaseStatusInput(List<String> ids) {
        return input(ids, "english", "{\"include_release\":true}");
    }

    /**
     * {@code release.is_early_access}, {@code is_free} ve {@code release.is_coming_soon}: Steam bunları yalnız {@code true} iken gönderir,
     * diğerlerinde alanı hiç göndermez. Sonuçta yalnız {@code release} bilgisi dönen (Steam'in tanıdığı ve erişilebilir) oyunlar yer alır;
     * yokluk false sayılır. Tanınmayan oyun (success=15, release yok) için değer uydurulmaz.
     */
    static Map<String, GameProvider.ReleaseStatus> parseReleaseStatus(List<String> requested, byte[] body) {
        Map<String, GameProvider.ReleaseStatus> result = new LinkedHashMap<>();
        for (JsonNode item : storeItemNodes(body)) {
            String appId = appId(item, requested);
            JsonNode release = item.path("release");
            if (appId != null && release.isObject()) {
                result.put(appId, new GameProvider.ReleaseStatus(release.path("is_early_access").asBoolean(false),
                        item.path("is_free").asBoolean(false), release.path("is_coming_soon").asBoolean(false),
                        adult(item.path("content_descriptorids")), item.hasNonNull("type") ? Integer.valueOf(item.get("type").asInt()) : null));
            }
        }
        return result;
    }

    /** Steam içerik tanımlayıcıları: 3 (Adult Only Sexual Content) veya 4 (Frequent Nudity or Sexual Content) yetişkin içeriktir; 1, 2 ve 5 daha hafiftir. */
    static boolean adult(JsonNode descriptors) {
        if (!descriptors.isArray()) {
            return false;
        }
        for (JsonNode descriptor : descriptors) {
            int id = descriptor.asInt(-1);
            if (id == 3 || id == 4) {
                return true;
            }
        }
        return false;
    }

    /** İki büyük harfli ülke kodu; aksi INVALID_PROVIDER_REQUEST (istek gövdesine yalnız doğrulanmış kod yazılır). */
    static String countryCode(String country) {
        if (country == null || !country.matches("[A-Z]{2}")) {
            throw new ProviderException("INVALID_PROVIDER_REQUEST");
        }
        return country;
    }

    /** Ülke bazlı satın alma seçenekleri (tüm paketler) isteği. */
    static String priceInput(List<String> ids, String country) {
        return input(ids, "english", countryCode(country), "{\"include_all_purchase_options\":true}");
    }

    /** Aynı istek gövdesi, sabit {@code data_request} ile (diğer ayrıştırıcı sınıflar için). */
    static String rawInput(List<String> ids, String steamLanguage, String dataRequest) {
        return input(ids, steamLanguage, dataRequest);
    }

    /** JSON yalnız doğrulanmış sayılardan ve sabit anahtarlardan kurulur. */
    private static String input(List<String> ids, String steamLanguage, String dataRequest) {
        return input(ids, steamLanguage, "US", dataRequest);
    }

    private static String input(List<String> ids, String steamLanguage, String country, String dataRequest) {
        String appIds = ids.stream().map(id -> "{\"appid\":" + id + "}").collect(Collectors.joining(","));
        return "{\"ids\":[" + appIds + "],\"context\":{\"language\":\"" + steamLanguage + "\",\"country_code\":\"" + country + "\"},\"data_request\":" + dataRequest + "}";
    }

    static Map<String, String> parseShortDescriptions(List<String> requested, byte[] body) {
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonNode item : storeItemNodes(body)) {
            String appId = appId(item, requested);
            JsonNode description = item.path("basic_info").path("short_description");
            if (appId != null && description.isString()) {
                String text = description.asString().strip();
                if (!text.isEmpty() && text.length() <= 16000) {
                    result.put(appId, text);
                }
            }
        }
        return result;
    }

    static Map<String, GameProvider.StoreItem> parseStoreItems(List<String> requested, byte[] body) {
        Map<String, GameProvider.StoreItem> result = new LinkedHashMap<>();
        for (JsonNode item : storeItemNodes(body)) {
            String appId = appId(item, requested);
            if (appId == null) {
                continue;
            }
            JsonNode basic = item.path("basic_info");
            String header = headerUrl(appId, item.path("assets"));
            Set<String> developers = names(basic.path("developers"));
            Set<String> publishers = names(basic.path("publishers"));
            List<GameProvider.LanguageSupport> languages = languages(item.path("supported_languages"));
            if (header != null || !developers.isEmpty() || !publishers.isEmpty() || !languages.isEmpty()) {
                result.put(appId, new GameProvider.StoreItem(header, developers, publishers, languages));
            }
        }
        return result;
    }

    static JsonNode storeItemNodes(byte[] body) {
        JsonNode items;
        try {
            JsonNode root = JSON.readTree(body);
            items = root == null ? null : root.path("response").path("store_items");
        } catch (JacksonException _) {
            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        }
        if (items == null || !items.isArray()) {
            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        }
        return items;
    }

    /** İstenen uygulamanın kimliği; istenmemiş veya kimliksiz öğe için null. */
    static String appId(JsonNode item, List<String> requested) {
        String appId = item.hasNonNull("appid") ? item.get("appid").asString() : null;
        return appId != null && requested.contains(appId) ? appId : null;
    }

    private static String headerUrl(String appId, JsonNode assets) {
        if (!assets.hasNonNull("asset_url_format") || !assets.hasNonNull("header")) {
            return null;
        }
        String format = assets.get("asset_url_format").asString();
        String header = assets.get("header").asString();
        if (!format.contains("${FILENAME}") || header.contains("/..") || header.startsWith("/")) {
            return null;
        }
        String url = ProviderCoverPolicy.steamHeader(ASSET_BASE + format.replace("${FILENAME}", header));
        // Adres başka bir uygulamaya ait olamaz.
        return url != null && url.contains("/apps/" + appId + "/") ? url : null;
    }

    private static Set<String> names(JsonNode array) {
        Set<String> result = new LinkedHashSet<>();
        if (!array.isArray()) {
            return result;
        }
        for (JsonNode item : array) {
            String name = item.hasNonNull("name") ? item.get("name").asString().strip() : "";
            if (!name.isEmpty() && name.length() <= 256 && result.size() < 50) {
                result.add(name);
            }
        }
        return result;
    }

    /** Arayüz desteği listelenen her dil için SUPPORTED; ses yalnız {@code full_audio}, altyazı yalnız {@code subtitles} true ise SUPPORTED, aksi UNKNOWN. */
    private static List<GameProvider.LanguageSupport> languages(JsonNode array) {
        Map<String, GameProvider.LanguageSupport> byCode = new LinkedHashMap<>();
        if (!array.isArray()) {
            return new ArrayList<>();
        }
        for (JsonNode entry : array) {
            String code = entry.path("supported").asBoolean(false) && entry.hasNonNull("elanguage") ? LANGUAGE_CODES.get(entry.get("elanguage").asInt(-1)) : null;
            if (code != null) {
                byCode.putIfAbsent(code, new GameProvider.LanguageSupport(code, LanguageSupportStatus.SUPPORTED,
                        flag(entry, "full_audio"), flag(entry, "subtitles")));
            }
        }
        return new ArrayList<>(byCode.values());
    }

    private static LanguageSupportStatus flag(JsonNode entry, String name) {
        return entry.path(name).asBoolean(false) ? LanguageSupportStatus.SUPPORTED : LanguageSupportStatus.UNKNOWN;
    }
}
