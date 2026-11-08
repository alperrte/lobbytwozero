package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GameProvider.CatalogItem;
import com.ltz.game.application.port.GameProvider.CatalogPage;
import com.ltz.game.application.port.GameProvider.CatalogQuery;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.client.dto.response.SteamCatalogEnvelope;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Steam GetAppList yanıtını güvenilmez girdi sayıp katalog sayfasına çevirir (durumsuz). Kayıt düzeyi hata yalnız o kaydı atlar;
 * sıralama bozukluğu, zarf hatası ve aşırı yüksek geçersiz oranı sağlayıcı düzeyi hatadır ve tüm sayfayı reddeder.
 */
final class SteamCatalogNormalizer {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SteamCatalogNormalizer.class);
    private static final long MAX_UINT32 = 4294967295L;
    /** Bir sayfada bu sayıdan az atlama her zaman kayıt düzeyi kabul edilir (sayfanın %10'u aşılmadıkça). */
    private static final int MIN_SYSTEMIC_SKIPS = 5;

    private SteamCatalogNormalizer() { }

    /**
     * Kayıt düzeyi hata (boş/aşırı uzun ad, eksik veya geçersiz last_modified, boş kayıt) yalnız o kaydı atlar; cursor
     * atlanan geçerli appid'nin ilerisine taşınır. Sıralama bozukluğu, zarf hatası ve aşırı yüksek geçersiz oranı
     * sağlayıcı düzeyi hatadır ve tüm sayfayı reddeder (INVALID_PROVIDER_RESPONSE).
     */
    static CatalogPage normalize(CatalogQuery query, SteamCatalogEnvelope envelope) {
        var response = checkedResponse(query, envelope);
        List<SteamCatalogEnvelope.App> apps = response.apps() == null ? List.of() : response.apps();
        List<CatalogItem> items = new ArrayList<>();
        long previous = query.afterAppId();
        int skipped = 0;
        int unreadable = 0;
        for (var app : apps) {
            if (!readable(app)) {
                skipped++;
                unreadable++;
                LOG.warn("Steam kaydı atlandı: appid okunamadı veya aralık dışı.");
            } else {
                // Artmayan appid cursor semantiğini bozar; tek kayıt değil tüm yanıt güvenilmezdir.
                if (app.appid() <= previous) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
                previous = app.appid();
                String reason = invalidReason(app);
                if (reason == null) {
                    items.add(new CatalogItem(Long.toString(app.appid()), app.name().strip(), Instant.ofEpochSecond(app.lastModified())));
                } else {
                    skipped++;
                    LOG.warn("Steam kaydı atlandı: appid={} neden={}", app.appid(), reason);
                }
            }
        }
        rejectSystemicSkips(skipped, apps.size());
        previous = pastUnreadable(previous, unreadable, response);
        boolean hasMore = hasMore(query, response, apps.size());
        validateContinuation(response, previous, apps.isEmpty(), hasMore);
        return new CatalogPage(items, previous, hasMore, skipped);
    }


    private static boolean readable(SteamCatalogEnvelope.App app) {
        return app != null && app.appid() != null && app.appid() <= MAX_UINT32;
    }


    /** Sayfanın önemli bir kısmı geçersizse bu bir kayıt sorunu değil sağlayıcı sözleşme/format sorunudur. */
    private static void rejectSystemicSkips(int skipped, int total) {
        if (skipped > MIN_SYSTEMIC_SKIPS && skipped * 10 > total) {
            LOG.error("Steam sayfasında {}/{} kayıt geçersiz; sayfa sağlayıcı hatası sayıldı.", skipped, total);
            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        }
    }


    /** Sayfa sonundaki okunamayan kayıtlar cursor'ı geçemez; sağlayıcının bildirdiği son appid'ye geçilir (döngü olmaz). */
    private static long pastUnreadable(long previous, int unreadable, SteamCatalogEnvelope.Response response) {
        Long last = response.lastAppId();
        return unreadable > 0 && last != null && last > previous && last <= MAX_UINT32 ? last : previous;
    }


    /**
     * Zarf doğrulaması. Steam'in belgelenmiş (ve gerçek yanıtlarla doğrulanmış) iki davranışı kabul edilir: sonuç yoksa
     * yanıt tamamen boştur ({@code {"response":{}}}); son sayfada {@code have_more_results} ve {@code last_appid} gönderilmez.
     * {@code apps} yokken başka bir alan varsa yanıt bozuktur.
     */
    private static SteamCatalogEnvelope.Response checkedResponse(CatalogQuery query, SteamCatalogEnvelope envelope) {
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
    private static boolean hasMore(CatalogQuery query, SteamCatalogEnvelope.Response response, int count) {
        if (response.haveMoreResults() != null) return response.haveMoreResults();
        if (count == 0 && query.afterAppId() == 0 && query.modifiedSince() == null) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        return count >= query.size();
    }


    /** Kayıt geçerliyse null; aksi halde loglanabilir (kullanıcı verisi içermeyen) neden kodu. */
    private static String invalidReason(SteamCatalogEnvelope.App app) {
        if (app.name() == null || app.name().isBlank()) return "BLANK_NAME";
        if (app.name().length() > 256) return "NAME_TOO_LONG";
        if (app.lastModified() == null || app.lastModified() < 0 || app.lastModified() > MAX_UINT32) return "INVALID_LAST_MODIFIED";
        return null;
    }


    private static void validateContinuation(SteamCatalogEnvelope.Response response, long previous, boolean empty, boolean hasMore) {
        if (response.lastAppId() != null && response.lastAppId() != previous) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        if (hasMore && empty) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
    }
}
