package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.application.service.ProviderCoverPolicy;
import com.ltz.game.infrastructure.client.dto.response.SteamCatalogEnvelope;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sadece belgelenmiş GetAppList; mağaza scraping veya belgelenmemiş detay çağrısı yoktur. Hedef kapsam ana oyun ve DLC'dir:
 * {@code include_games} ve {@code include_dlc} açık; yazılım, video ve donanım kapalıdır. Steam yanıtı kayıt başına tür vermez
 * ve oyun/DLC filtresi soundtrack, demo veya araç gibi içeriği dışlamayı garanti etmez; bu yüzden gelen kayıtlar UNKNOWN
 * indekslenir ve tür yalnız IGDB kanıtıyla belirlenir.
 */
public class SteamGameProvider implements GameProvider {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SteamGameProvider.class);
    private static final long MAX_UINT32 = 4294967295L;
    /** Bir sayfada bu sayıdan az atlama her zaman kayıt düzeyi kabul edilir (sayfanın %10'u aşılmadıkça). */
    private static final int MIN_SYSTEMIC_SKIPS = 5;
    private static final String ENDPOINT = "https://api.steampowered.com/IStoreService/GetAppList/v1/";
    private final RestClient client;
    private final String key;
    private final boolean enabled;
    private final SteamRequestBudget budget;

    private final java.util.function.LongConsumer sleeper;

    public SteamGameProvider(RestClient client, String key, boolean enabled, SteamRequestBudget budget) {
        this(client, key, enabled, budget, SteamGameProvider::sleepMillis);
    }

    /** Bekleme ayrı verilebilir (testlerde saat ilerletir); üretimde iş parçacığını uyutur. */
    SteamGameProvider(RestClient client, String key, boolean enabled, SteamRequestBudget budget, java.util.function.LongConsumer sleeper) {
        this.client = client;
        this.key = key;
        this.enabled = enabled;
        this.budget = budget;
        this.sleeper = sleeper;
    }

    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new ProviderException("PROVIDER_TRANSPORT_ERROR");
        }
    }
    @Override public String provider() { return "STEAM"; }
    @Override public Set<Capability> capabilities() { return Set.of(Capability.CATALOG, Capability.INCREMENTAL_CATALOG); }

    @Override public CatalogPage catalog(CatalogQuery query) { return page(query, true); }

    /** Üst sınır: yaklaşık 65 sayfa (1000 kayıt) beklenir; sonsuz döngüye karşı güvenlik payı. */
    private static final int MAX_DLC_PAGES = 400;

    /**
     * Steam'in yalnız DLC olarak listelediği AppID'ler ({@code include_games=false}). Tür kanıtı değil, başka kaynağın (IGDB)
     * tür kanıtını doğrulamak içindir. Geçersiz kayıt atlanır (muhafazakâr: listede yok sayılır); zarf/sıra hatası taramayı düşürür.
     */
    @Override public Set<String> catalogDlcIds() {
        Set<String> ids = new java.util.HashSet<>();
        long after = 0;
        for (int pages = 0; pages < MAX_DLC_PAGES; pages++) {
            // İstek bütçesi saniyede 1 çağrıya izin verir: sayfalar arasında bekle, bütçe kısa süre doluysa bir kez daha dene.
            if (pages > 0) sleeper.accept(PAGE_PACE_MILLIS);
            CatalogPage page = pageWithinBudget(new CatalogQuery(after, null, 1000));
            page.items().forEach(item -> ids.add(item.externalId()));
            if (!page.hasMore()) return Set.copyOf(ids);
            after = page.nextAppId();
        }
        throw new ProviderException("INVALID_PROVIDER_RESPONSE");
    }

    private static final long PAGE_PACE_MILLIS = 1100;

    private static final String BROWSE_ENDPOINT = "https://api.steampowered.com/IStoreBrowseService/GetItems/v1/";
    private static final String ASSET_BASE = "https://shared.fastly.steamstatic.com/store_item_assets/";
    /** Bir istekte en çok bu kadar AppID (adres uzunluğu sınırı için). */
    static final int MAX_STORE_BATCH = 100;
    /**
     * Steam {@code ELanguage} numarası → katalog dil kodu. Eşleme, aynı oyunların mağaza sayfasındaki dil adlarıyla çapraz doğrulanarak
     * çıkarılmıştır (resmî sıralamadan farklı olabilir). Ayırt edilemeyen (20, 23) ve bilinmeyen numaralar bilerek ATLANIR: yanlış dil
     * etiketi yazmaktansa dil eksik kalır.
     */
    private static final java.util.Map<Integer, String> LANGUAGE_CODES = java.util.Map.ofEntries(
            java.util.Map.entry(0, "en-us"), java.util.Map.entry(1, "de-de"), java.util.Map.entry(2, "fr-fr"), java.util.Map.entry(3, "it-it"),
            java.util.Map.entry(4, "ko-kr"), java.util.Map.entry(5, "es-es"), java.util.Map.entry(6, "zh-cn"), java.util.Map.entry(7, "zh-tw"),
            java.util.Map.entry(8, "ru-ru"), java.util.Map.entry(9, "th-th"), java.util.Map.entry(10, "ja-jp"), java.util.Map.entry(11, "pt-pt"),
            java.util.Map.entry(12, "pl-pl"), java.util.Map.entry(13, "da-dk"), java.util.Map.entry(14, "nl-nl"), java.util.Map.entry(15, "fi-fi"),
            java.util.Map.entry(16, "nb-no"), java.util.Map.entry(17, "sv-se"), java.util.Map.entry(18, "hu-hu"), java.util.Map.entry(19, "cs-cz"),
            java.util.Map.entry(21, "tr-tr"), java.util.Map.entry(22, "pt-br"), java.util.Map.entry(24, "el-gr"), java.util.Map.entry(25, "ar"),
            java.util.Map.entry(26, "uk-ua"), java.util.Map.entry(27, "es-mx"), java.util.Map.entry(28, "vi-vn"), java.util.Map.entry(30, "id-id"));

    /**
     * Resmî {@code IStoreBrowseService/GetItems} (aynı API anahtarı, aynı istek bütçesi): oyun başına 16:9 başlık görseli, geliştirici, yayıncı ve
     * dil desteği (ses/altyazı bayraklarıyla). Yeni uygulamalarda başlık dosya adı içerik özetini taşır; adres yalnız Steam'in bildirdiği değerden
     * kurulur, uydurulmaz. Hiçbir alanı olmayan veya erişilemeyen uygulama sonuçta yer almaz.
     */
    @Override public java.util.Map<String, StoreItem> storeItems(java.util.Collection<String> externalIds) {
        if (!enabled || key == null || key.isBlank()) throw new ProviderException("PROVIDER_NOT_CONFIGURED");
        List<String> ids = externalIds == null ? List.of() : externalIds.stream().distinct().toList();
        if (ids.isEmpty()) return java.util.Map.of();
        if (ids.size() > MAX_STORE_BATCH || ids.stream().anyMatch(id -> id == null || !id.matches("[1-9]\\d{0,9}"))) throw new ProviderException("INVALID_PROVIDER_REQUEST");
        // JSON yalnız doğrulanmış sayılardan kurulur.
        String input = "{\"ids\":[" + ids.stream().map(id -> "{\"appid\":" + id + "}").collect(java.util.stream.Collectors.joining(","))
                + "],\"context\":{\"language\":\"english\",\"country_code\":\"US\"},\"data_request\":{\"include_assets\":true,"
                + "\"include_basic_info\":true,\"include_supported_languages\":true}}";
        acquireWithinBudget();
        byte[] body;
        try {
            body = client.get().uri(BROWSE_ENDPOINT + "?input_json={input}", input).header("x-webapi-key", key).exchange((request, response) -> {
                validateStatus(response.getStatusCode().value(), response.getHeaders().getFirst("Retry-After"));
                byte[] bytes = response.getBody().readNBytes(8_388_609);
                if (bytes.length > 8_388_608) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
                return bytes;
            });
        } catch (RestClientException _) {
            throw new ProviderException("PROVIDER_TRANSPORT_ERROR");
        }
        return parseStoreItems(ids, body);
    }

    /** Katalog dil kodu → Steam dil adı (yalnız ihtiyaç duyulan diller; diğerleri için gereksiz istek atılmaz). */
    private static final java.util.Map<String, String> STEAM_LANGUAGE_NAMES = java.util.Map.of("en", "english", "tr", "turkish");

    /**
     * Resmî {@code IStoreBrowseService/GetItems} {@code basic_info.short_description}, {@code context.language} ile istenen dilde. Steam o dilde
     * mağaza metni yoksa İngilizceye düşer; ayrımı çağıran yapar (aynı oyun {@code en} ile de sorulur).
     */
    @Override public java.util.Map<String, String> shortDescriptions(java.util.Collection<String> externalIds, String language) {
        String steamLanguage = language == null ? null : STEAM_LANGUAGE_NAMES.get(language);
        if (steamLanguage == null) throw new ProviderException("INVALID_PROVIDER_REQUEST");
        if (!enabled || key == null || key.isBlank()) throw new ProviderException("PROVIDER_NOT_CONFIGURED");
        List<String> ids = externalIds == null ? List.of() : externalIds.stream().distinct().toList();
        if (ids.isEmpty()) return java.util.Map.of();
        if (ids.size() > MAX_STORE_BATCH || ids.stream().anyMatch(id -> id == null || !id.matches("[1-9]\\d{0,9}"))) throw new ProviderException("INVALID_PROVIDER_REQUEST");
        String input = "{\"ids\":[" + ids.stream().map(id -> "{\"appid\":" + id + "}").collect(java.util.stream.Collectors.joining(","))
                + "],\"context\":{\"language\":\"" + steamLanguage + "\",\"country_code\":\"US\"},\"data_request\":{\"include_basic_info\":true}}";
        acquireWithinBudget();
        byte[] body;
        try {
            body = client.get().uri(BROWSE_ENDPOINT + "?input_json={input}", input).header("x-webapi-key", key).exchange((request, response) -> {
                validateStatus(response.getStatusCode().value(), response.getHeaders().getFirst("Retry-After"));
                byte[] bytes = response.getBody().readNBytes(8_388_609);
                if (bytes.length > 8_388_608) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
                return bytes;
            });
        } catch (RestClientException _) {
            throw new ProviderException("PROVIDER_TRANSPORT_ERROR");
        }
        return parseShortDescriptions(ids, body);
    }

    static java.util.Map<String, String> parseShortDescriptions(List<String> requested, byte[] body) {
        tools.jackson.databind.JsonNode items;
        try {
            var root = tools.jackson.databind.json.JsonMapper.builder().build().readTree(body);
            items = root == null ? null : root.path("response").path("store_items");
        } catch (tools.jackson.core.JacksonException _) {
            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        }
        if (items == null || !items.isArray()) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        for (var item : items) {
            String appId = item.hasNonNull("appid") ? item.get("appid").asString() : null;
            var description = item.path("basic_info").path("short_description");
            if (appId == null || !requested.contains(appId) || !description.isString()) continue;
            String text = description.asString().strip();
            if (!text.isEmpty() && text.length() <= 16000) result.put(appId, text);
        }
        return result;
    }

    static java.util.Map<String, StoreItem> parseStoreItems(List<String> requested, byte[] body) {
        tools.jackson.databind.JsonNode items;
        try {
            var root = tools.jackson.databind.json.JsonMapper.builder().build().readTree(body);
            items = root == null ? null : root.path("response").path("store_items");
        } catch (tools.jackson.core.JacksonException _) {
            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        }
        if (items == null || !items.isArray()) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        java.util.Map<String, StoreItem> result = new java.util.LinkedHashMap<>();
        for (var item : items) {
            String appId = item.hasNonNull("appid") ? item.get("appid").asString() : null;
            if (appId == null || !requested.contains(appId)) continue;
            String header = headerUrl(appId, item.path("assets"));
            var basic = item.path("basic_info");
            var developers = names(basic.path("developers"));
            var publishers = names(basic.path("publishers"));
            var languages = languages(item.path("supported_languages"));
            if (header != null || !developers.isEmpty() || !publishers.isEmpty() || !languages.isEmpty()) {
                result.put(appId, new StoreItem(header, developers, publishers, languages));
            }
        }
        return result;
    }

    private static String headerUrl(String appId, tools.jackson.databind.JsonNode assets) {
        if (!assets.hasNonNull("asset_url_format") || !assets.hasNonNull("header")) return null;
        String format = assets.get("asset_url_format").asString();
        String header = assets.get("header").asString();
        if (!format.contains("${FILENAME}") || header.contains("/..") || header.startsWith("/")) return null;
        String url = ProviderCoverPolicy.steamHeader(ASSET_BASE + format.replace("${FILENAME}", header));
        // Adres başka bir uygulamaya ait olamaz.
        return url != null && url.contains("/apps/" + appId + "/") ? url : null;
    }

    private static java.util.Set<String> names(tools.jackson.databind.JsonNode array) {
        java.util.Set<String> result = new java.util.LinkedHashSet<>();
        if (array.isArray()) {
            for (var item : array) {
                String name = item.hasNonNull("name") ? item.get("name").asString().strip() : "";
                if (!name.isEmpty() && name.length() <= 256 && result.size() < 50) result.add(name);
            }
        }
        return result;
    }

    /** Arayüz desteği listelenen her dil için SUPPORTED; ses yalnız {@code full_audio}, altyazı yalnız {@code subtitles} true ise SUPPORTED, aksi UNKNOWN. */
    private static List<LanguageSupport> languages(tools.jackson.databind.JsonNode array) {
        java.util.Map<String, LanguageSupport> byCode = new java.util.LinkedHashMap<>();
        if (array.isArray()) {
            for (var entry : array) {
                if (!entry.path("supported").asBoolean(false) || !entry.hasNonNull("elanguage")) continue;
                String code = LANGUAGE_CODES.get(entry.get("elanguage").asInt(-1));
                if (code == null || byCode.containsKey(code)) continue;
                byCode.put(code, new LanguageSupport(code, com.ltz.game.domain.enums.LanguageSupportStatus.SUPPORTED,
                        entry.path("full_audio").asBoolean(false) ? com.ltz.game.domain.enums.LanguageSupportStatus.SUPPORTED : com.ltz.game.domain.enums.LanguageSupportStatus.UNKNOWN,
                        entry.path("subtitles").asBoolean(false) ? com.ltz.game.domain.enums.LanguageSupportStatus.SUPPORTED : com.ltz.game.domain.enums.LanguageSupportStatus.UNKNOWN));
            }
        }
        return new java.util.ArrayList<>(byCode.values());
    }

    /** İstek bütçesi saniyede 1 çağrıya izin verir; kısa süre doluysa bekleyip yeniden dener. */
    private void acquireWithinBudget() {
        for (int attempt = 0; ; attempt++) {
            try {
                budget.acquire();
                return;
            } catch (ProviderException error) {
                java.time.Duration wait = error.retryAfter();
                if (!"RATE_LIMITED".equals(error.code()) || wait == null || wait.compareTo(java.time.Duration.ofSeconds(2)) > 0 || attempt >= 3) throw error;
                sleeper.accept(wait.toMillis() + 50);
            }
        }
    }

    private CatalogPage pageWithinBudget(CatalogQuery query) {
        for (int attempt = 0; ; attempt++) {
            try {
                return page(query, false);
            } catch (ProviderException error) {
                java.time.Duration wait = error.retryAfter();
                if (!"RATE_LIMITED".equals(error.code()) || wait == null || wait.compareTo(java.time.Duration.ofSeconds(2)) > 0 || attempt >= 3) throw error;
                sleeper.accept(wait.toMillis() + 50);
            }
        }
    }

    private CatalogPage page(CatalogQuery query, boolean includeGames) {
        if (!enabled || key == null || key.isBlank()) throw new ProviderException("PROVIDER_NOT_CONFIGURED");
        budget.acquire();
        // JSON anahtar isimleri sabittir; yalnız doğrulanmış sayısal değerler yerleştirilir.
        String input = "{\"include_games\":" + includeGames + ",\"include_dlc\":true,\"include_software\":false,"
                + "\"include_videos\":false,\"include_hardware\":false,\"last_appid\":" + query.afterAppId()
                + ",\"max_results\":" + query.size()
                + (query.modifiedSince() == null ? "" : ",\"if_modified_since\":" + query.modifiedSince().getEpochSecond()) + "}";
        try {
            SteamCatalogEnvelope envelope = client.get().uri(ENDPOINT + "?input_json={input}", input)
                    .header("x-webapi-key", key)
                    .exchange((request, response) -> {
                        validateStatus(response.getStatusCode().value(), response.getHeaders().getFirst("Retry-After"));
                        byte[] body = response.getBody().readNBytes(1048577);
                        if (body.length > 1048576) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
                        try {
                            return tools.jackson.databind.json.JsonMapper.builder().build()
                                    .readValue(body, SteamCatalogEnvelope.class);
                        } catch (tools.jackson.core.JacksonException _) {
                            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
                        }
                    });
            return normalize(query, envelope);
        } catch (RestClientException _) {
            // Ağ istisnasının ayrıntıları ve cause dışarı taşınmaz.
            throw new ProviderException("PROVIDER_TRANSPORT_ERROR");
        }
    }
    private void validateStatus(int status, String retryAfterHeader) {
        if (status == 401 || status == 403) throw new ProviderException("PROVIDER_ACCESS_DENIED");
        if (status == 429) throw new ProviderException("RATE_LIMITED", retryAfter(retryAfterHeader));
        if (status >= 500) throw new ProviderException("PROVIDER_UNAVAILABLE", retryAfter(retryAfterHeader));
        if (status != 200) throw new ProviderException("PROVIDER_REQUEST_REJECTED");
    }
    private java.time.Duration retryAfter(String value) {
        if (value == null) return null;
        try {
            if (value.matches("\\d+")) return retryAfterSeconds(value);
            var time = java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            var delay = java.time.Duration.between(Instant.now(), time);
            return delay.isNegative() ? null : delay;
        } catch (java.time.DateTimeException _) {
            return null;
        }
    }
    private java.time.Duration retryAfterSeconds(String value) {
        try { return java.time.Duration.ofSeconds(Long.parseLong(value)); }
        catch (NumberFormatException _) { return java.time.Duration.ofDays(2); }
    }
    /**
     * Kayıt düzeyi hata (boş/aşırı uzun ad, eksik veya geçersiz last_modified, boş kayıt) yalnız o kaydı atlar; cursor
     * atlanan geçerli appid'nin ilerisine taşınır. Sıralama bozukluğu, zarf hatası ve aşırı yüksek geçersiz oranı
     * sağlayıcı düzeyi hatadır ve tüm sayfayı reddeder (INVALID_PROVIDER_RESPONSE).
     */
    private CatalogPage normalize(CatalogQuery query, SteamCatalogEnvelope envelope) {
        var response = checkedResponse(query, envelope);
        List<SteamCatalogEnvelope.App> apps = response.apps() == null ? List.of() : response.apps();
        List<CatalogItem> items = new ArrayList<>();
        long previous = query.afterAppId();
        int skipped = 0;
        int unreadable = 0;
        for (var app : apps) {
            if (app == null || app.appid() == null || app.appid() > MAX_UINT32) {
                skipped++;
                unreadable++;
                LOG.warn("Steam kaydı atlandı: appid okunamadı veya aralık dışı.");
                continue;
            }
            // Artmayan appid cursor semantiğini bozar; tek kayıt değil tüm yanıt güvenilmezdir.
            if (app.appid() <= previous) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
            previous = app.appid();
            String reason = invalidReason(app);
            if (reason != null) {
                skipped++;
                LOG.warn("Steam kaydı atlandı: appid={} neden={}", app.appid(), reason);
                continue;
            }
            items.add(new CatalogItem(Long.toString(app.appid()), app.name().strip(), Instant.ofEpochSecond(app.lastModified())));
        }
        if (skipped > MIN_SYSTEMIC_SKIPS && skipped * 10 > apps.size()) {
            // Sayfanın önemli bir kısmı geçersizse bu bir kayıt sorunu değil sağlayıcı sözleşme/format sorunudur.
            LOG.error("Steam sayfasında {}/{} kayıt geçersiz; sayfa sağlayıcı hatası sayıldı.", skipped, apps.size());
            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        }
        // Sayfa sonundaki okunamayan kayıtlar cursor'ı geçemez; sağlayıcının bildirdiği son appid'ye geçilir (döngü olmaz).
        if (unreadable > 0 && response.lastAppId() != null && response.lastAppId() > previous
                && response.lastAppId() <= MAX_UINT32) previous = response.lastAppId();
        boolean hasMore = hasMore(query, response, apps.size());
        validateContinuation(response, previous, apps.isEmpty(), hasMore);
        return new CatalogPage(items, previous, hasMore, skipped);
    }

    /**
     * Zarf doğrulaması. Steam'in belgelenmiş (ve gerçek yanıtlarla doğrulanmış) iki davranışı kabul edilir: sonuç yoksa
     * yanıt tamamen boştur ({@code {"response":{}}}); son sayfada {@code have_more_results} ve {@code last_appid} gönderilmez.
     * {@code apps} yokken başka bir alan varsa yanıt bozuktur.
     */
    private SteamCatalogEnvelope.Response checkedResponse(CatalogQuery query, SteamCatalogEnvelope envelope) {
        if (envelope == null || envelope.response() == null) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        var response = envelope.response();
        boolean emptyResponse = response.apps() == null && response.haveMoreResults() == null && response.lastAppId() == null;
        if ((response.apps() == null && !emptyResponse) || (response.apps() != null && response.apps().size() > query.size())) {
            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        }
        return response;
    }

    /**
     * Devam bilgisi. {@code have_more_results} varsa o geçerlidir. Yoksa Steam son sayfadadır: tam dolu bir sayfa son olamaz
     * (devam edilir, bir sonraki boş yanıt bitirir); kısa veya boş sayfa sondur. Kayıtsız başlangıç (cursor 0, artımsız
     * ilk aktarım) boş gelirse bozuk sayılır; böylece eksik/bozuk bir yanıt senkronu yanlışlıkla COMPLETE yapamaz.
     */
    private boolean hasMore(CatalogQuery query, SteamCatalogEnvelope.Response response, int count) {
        if (response.haveMoreResults() != null) return response.haveMoreResults();
        if (count == 0 && query.afterAppId() == 0 && query.modifiedSince() == null) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        return count >= query.size();
    }

    /** Kayıt geçerliyse null; aksi halde loglanabilir (kullanıcı verisi içermeyen) neden kodu. */
    private String invalidReason(SteamCatalogEnvelope.App app) {
        if (app.name() == null || app.name().isBlank()) return "BLANK_NAME";
        if (app.name().length() > 256) return "NAME_TOO_LONG";
        if (app.lastModified() == null || app.lastModified() < 0 || app.lastModified() > MAX_UINT32) return "INVALID_LAST_MODIFIED";
        return null;
    }

    private void validateContinuation(SteamCatalogEnvelope.Response response, long previous, boolean empty, boolean hasMore) {
        if (response.lastAppId() != null && response.lastAppId() != previous) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        if (hasMore && empty) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
    }
}
