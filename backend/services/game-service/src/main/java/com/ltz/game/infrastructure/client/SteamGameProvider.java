package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.client.dto.response.SteamCatalogEnvelope;
import java.time.Instant;
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

    /**
     * Resmî {@code IStoreBrowseService/GetItems} (aynı API anahtarı, aynı istek bütçesi): oyun başına 16:9 başlık görseli, geliştirici, yayıncı ve
     * dil desteği (ses/altyazı bayraklarıyla). Yeni uygulamalarda başlık dosya adı içerik özetini taşır; adres yalnız Steam'in bildirdiği değerden
     * kurulur, uydurulmaz. Hiçbir alanı olmayan veya erişilemeyen uygulama sonuçta yer almaz.
     */
    @Override public java.util.Map<String, StoreItem> storeItems(java.util.Collection<String> externalIds) {
        requireConfigured();
        List<String> ids = SteamStoreBrowse.validIds(externalIds);
        if (ids.isEmpty()) return java.util.Map.of();
        return SteamStoreBrowse.parseStoreItems(ids, browse(SteamStoreBrowse.storeItemsInput(ids)));
    }

    /** {@code release.is_early_access} (Steam toplu mağaza öğeleri); yalnız Steam'in tanıdığı uygulamalar sonuçta yer alır. */
    @Override public java.util.Map<String, Boolean> earlyAccess(java.util.Collection<String> externalIds) {
        requireConfigured();
        List<String> ids = SteamStoreBrowse.validIds(externalIds);
        if (ids.isEmpty()) return java.util.Map.of();
        return SteamStoreBrowse.parseEarlyAccess(ids, browse(SteamStoreBrowse.earlyAccessInput(ids)));
    }

    /**
     * {@code basic_info.short_description}, {@code context.language} ile istenen dilde. Steam o dilde mağaza metni yoksa İngilizceye düşer;
     * ayrımı çağıran yapar (aynı oyun {@code en} ile de sorulur). Yapılandırılmamış dil için istek atılmaz.
     */
    @Override public java.util.Map<String, String> shortDescriptions(java.util.Collection<String> externalIds, String language) {
        String steamLanguage = SteamStoreBrowse.steamLanguage(language);
        requireConfigured();
        List<String> ids = SteamStoreBrowse.validIds(externalIds);
        if (ids.isEmpty()) return java.util.Map.of();
        return SteamStoreBrowse.parseShortDescriptions(ids, browse(SteamStoreBrowse.shortDescriptionInput(ids, steamLanguage)));
    }

    private void requireConfigured() {
        if (!enabled || key == null || key.isBlank()) throw new ProviderException("PROVIDER_NOT_CONFIGURED");
    }

    /** Toplu mağaza isteği: bütçe beklemesi, durum doğrulaması ve gövde sınırı; ağ ayrıntıları dışarı taşınmaz. */
    private byte[] browse(String input) {
        retrying(() -> {
            budget.acquire();
            return Boolean.TRUE;
        });
        try {
            return client.get().uri(BROWSE_ENDPOINT + "?input_json={input}", input).header("x-webapi-key", key).exchange((request, response) -> {
                validateStatus(response.getStatusCode().value(), response.getHeaders().getFirst("Retry-After"));
                byte[] bytes = response.getBody().readNBytes(SteamStoreBrowse.MAX_BODY + 1);
                if (bytes.length > SteamStoreBrowse.MAX_BODY) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
                return bytes;
            });
        } catch (RestClientException _) {
            throw new ProviderException("PROVIDER_TRANSPORT_ERROR");
        }
    }

    /** İstek bütçesi saniyede 1 çağrıya izin verir: kısa bir hız sınırı beklemesi (en çok 2 sn) birkaç kez beklenip yeniden denenir. */
    private <T> T retrying(java.util.function.Supplier<T> action) {
        for (int attempt = 0; ; attempt++) {
            try {
                return action.get();
            } catch (ProviderException error) {
                if (!shortRateLimit(error) || attempt >= 3) throw error;
                sleeper.accept(error.retryAfter().toMillis() + 50);
            }
        }
    }

    private static boolean shortRateLimit(ProviderException error) {
        java.time.Duration wait = error.retryAfter();
        return "RATE_LIMITED".equals(error.code()) && wait != null && wait.compareTo(java.time.Duration.ofSeconds(2)) <= 0;
    }

    private CatalogPage pageWithinBudget(CatalogQuery query) {
        return retrying(() -> page(query, false));
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
            return SteamCatalogNormalizer.normalize(query, envelope);
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
}
