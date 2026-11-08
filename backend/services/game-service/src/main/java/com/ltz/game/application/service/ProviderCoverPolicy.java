package com.ltz.game.application.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sağlayıcıdan gelen kapak adresi politikası. Yalnız bilinen iki görsel kaynağı kabul edilir (IGDB görsel CDN'i ve Steam
 * mağaza varlıkları); adres gösterim için saklanır, sunucu bu adreslere istek atmaz. Kapak kaynağı önceliği:
 * yönetici (hiç ezilmez) &gt; Steam başlık görseli (16:9, kartlara uygun) &gt; IGDB kapağı.
 */
public final class ProviderCoverPolicy {
    private static final Pattern IGDB = Pattern.compile("^https://images\\.igdb\\.com/igdb/image/upload/t_[a-z0-9_]{3,32}/[A-Za-z0-9]{3,64}\\.jpg$");
    private static final Pattern STEAM = Pattern.compile("^https://shared\\.(?:fastly|akamai|cloudflare)\\.steamstatic\\.com/store_item_assets/steam/apps/(\\d{1,10})(?:/([0-9a-f]{32,64}))?/header((?:_alt_assets_\\d{1,3})?)\\.jpg(?:\\?[A-Za-z0-9=&_.-]{0,64})?$");

    private ProviderCoverPolicy() { }

    /** IGDB kapak adresi; {@code imageId} IGDB'nin görsel kimliğidir (yalnız harf/rakam), aksi halde null. */
    public static String igdbCover(String imageId) {
        if (imageId == null || !imageId.matches("[A-Za-z0-9]{3,64}")) return null;
        return "https://images.igdb.com/igdb/image/upload/t_cover_big/" + imageId + ".jpg";
    }

    /**
     * Steam {@code header_image} adresini kalıcı biçime getirir: sunucu adı sabit (shared.fastly), zaman damgası sorgusu atılır.
     * Yeni uygulamalarda yol içerik özetini ({@code /apps/ID/HASH/header.jpg}) taşır; eski {@code /apps/ID/header.jpg} yolu bu
     * uygulamalar için 404 verir, bu yüzden adres yalnız Steam'in bildirdiği değerden türetilir, uydurulmaz.
     */
    public static String steamHeader(String raw) {
        if (raw == null) return null;
        Matcher matcher = STEAM.matcher(raw.strip());
        if (!matcher.matches()) return null;
        String hash = matcher.group(2);
        String file = "header" + matcher.group(3);
        return "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/" + matcher.group(1) + (hash == null ? "" : "/" + hash) + "/" + file + ".jpg";
    }

    /** Sağlayıcı kapağı olarak saklanabilir mi (IGDB veya normalleştirilmiş Steam adresi). */
    public static boolean accepts(String url) {
        return url != null && url.length() <= 512 && (IGDB.matcher(url).matches() || (STEAM.matcher(url).matches() && url.indexOf('?') < 0));
    }

    /** Önceliği: büyük sayı korunur. Bilinmeyen/yönetici kaynağı en yüksektir (asla ezilmez). */
    static int rank(String provider) {
        if ("IGDB".equals(provider)) return 1;
        if ("STEAM".equals(provider)) return 2;
        return 3;
    }
}
