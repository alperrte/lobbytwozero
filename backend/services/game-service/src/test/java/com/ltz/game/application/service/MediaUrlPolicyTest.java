package com.ltz.game.application.service;

import com.ltz.game.domain.enums.DeveloperMediaType;
import com.ltz.game.domain.exception.InvalidGameRequestException;
import com.ltz.game.infrastructure.config.MediaUrlProperties;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * URL doğrulaması yalnız metin üzerinde çalışır; sunucu hiçbir adrese istek atmaz. Kabul örnekleri, 2026-10-21'de gerçek
 * Steam mağaza sayfası ve örnek URL'lerle (HTTP 200) doğrulanmış biçimlerdir; test ağ erişimi kullanmaz.
 * Politika, uygulamanın gerçek varsayılan yapılandırmasıyla (application.properties) aynı kaynaklarla kurulur.
 */
class MediaUrlPolicyTest {
    static final Set<String> IMAGE = Set.of("shared.akamai.steamstatic.com/store_item_assets/steam/apps/",
            "shared.fastly.steamstatic.com/store_item_assets/steam/apps/", "cdn.akamai.steamstatic.com/steam/apps/");
    static final Set<String> VIDEO = Set.of("www.youtube.com/watch", "www.youtube.com/embed/", "www.youtube-nocookie.com/embed/",
            "youtu.be/", "vimeo.com/", "player.vimeo.com/video/");
    static final Set<String> DEMO = Set.of("store.steampowered.com/app/");
    final MediaUrlPolicy policy = new MediaUrlPolicy(new MediaUrlProperties(IMAGE, VIDEO, DEMO));

    void accepts(DeveloperMediaType type, String... urls) {
        for (String url : urls) assertEquals(url, policy.validate(type, url), url);
    }

    void rejects(DeveloperMediaType type, List<String> urls) {
        for (String url : urls) assertThrows(InvalidGameRequestException.class, () -> policy.validate(type, url), url);
    }

    @Test void acceptsRealSteamStoreImageUrls() {
        accepts(DeveloperMediaType.IMAGE,
                "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/730/header.jpg",
                "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/730/162664aa5da85f418105350c5d67ca565f6c3713/header.jpg?t=1791824795",
                "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/730/6328493f4cc4a8fb83e1149be1cac5a7823fe588/capsule_616x353.jpg",
                "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/570/capsule_616x353.jpg",
                "https://cdn.akamai.steamstatic.com/steam/apps/570/header.jpg",
                "https://cdn.akamai.steamstatic.com/steam/apps/570/ss_0123456789abcdef.1920x1080.PNG?t=1");
    }

    @Test void acceptsRealYouTubeVimeoAndSteamDemoUrls() {
        accepts(DeveloperMediaType.VIDEO,
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://www.youtube.com/embed/dQw4w9WgXcQ",
                "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ", "https://youtu.be/dQw4w9WgXcQ?t=10",
                "https://vimeo.com/76979871", "https://player.vimeo.com/video/76979871?h=abc123");
        accepts(DeveloperMediaType.DEMO, "https://store.steampowered.com/app/730/", "https://store.steampowered.com/app/730/CounterStrike_2/");
        // Metin baş/son boşluklarından arındırılır.
        assertEquals("https://youtu.be/dQw4w9WgXcQ", policy.validate(DeveloperMediaType.VIDEO, "  https://youtu.be/dQw4w9WgXcQ \n"));
    }

    @Test void rejectsHostImpersonationSchemesPortsCredentialsAndIpAddresses() {
        for (var type : DeveloperMediaType.values()) {
            rejects(type, List.of("", "   ", "http://youtu.be/dQw4w9WgXcQ", "ftp://youtu.be/x", "//youtu.be/x", "youtu.be/x", "javascript:alert(1)",
                    "data:text/html;base64,AAAA", "https://127.0.0.1/steam/apps/1/a.jpg", "https://[::1]/x", "https://0x7f.1/x", "https://localhost/x",
                    "https://169.254.169.254/latest/meta-data", "https://2130706433/x", "https://youtu.be.evil.example/dQw4w9WgXcQ",
                    "https://evil.example/youtu.be/dQw4w9WgXcQ", "https://youtu.be@evil.example/x", "https://user:pass@youtu.be/x",
                    "https://youtu.be:8443/x", "https://youtu.be:443/x", "https://sub.youtu.be/x", "https://xyoutu.be/x", "https://youtu.be./x",
                    "https://youtu.be%2f@evil.example/", "https://%79outu.be/x", "https://youtu.be\\@evil.example/", "https://youtu.be/x#frag",
                    "https://youtu.be/a b", "https://youtu.be/ç", "https://yoütu.be/x", "https://youtu.be/<script>"));
        }
        rejects(DeveloperMediaType.VIDEO, List.of("https://www.youtube.com/watch?v=" + "a".repeat(1100)));
        assertThrows(InvalidGameRequestException.class, () -> policy.validate(DeveloperMediaType.VIDEO, null));
    }

    @Test void restrictsEachSourceToItsDocumentedPathsInsteadOfTheWholeHost() {
        rejects(DeveloperMediaType.VIDEO, List.of(
                "https://www.youtube.com/redirect?q=https%3A%2F%2Fevil.example", "https://www.youtube.com/results?search_query=x",
                "https://www.youtube.com/@channel", "https://www.youtube.com/", "https://www.youtube.com/watchfoo?v=x",
                "https://www.youtube.com/embed/", "https://www.youtube.com/embed", "https://youtu.be/", "https://youtu.be",
                "https://player.vimeo.com/api/player.js", "https://player.vimeo.com/video/", "https://vimeo.com/", "https://www.youtube-nocookie.com/watch?v=x"));
        rejects(DeveloperMediaType.DEMO, List.of("https://store.steampowered.com/", "https://store.steampowered.com/login/", "https://store.steampowered.com/app/",
                "https://store.steampowered.com/appfoo/1", "https://steamcommunity.com/app/730"));
        rejects(DeveloperMediaType.IMAGE, List.of(
                // Sağlayıcının asıl varlık yolu dışı, kullanıcı içeriği veya site varlıkları
                "https://shared.akamai.steamstatic.com/community_assets/images/steamworks_docs/english/Header_1.jpg",
                "https://store.akamai.steamstatic.com/public/css/main.css", "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/",
                "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps", "https://cdn.akamai.steamstatic.com/steamcommunity/public/images/a.jpg",
                // Listede olmayan hostlar: 301 veren, eski veya ilgisiz CDN'ler ve video/küçük resim servisleri
                "https://shared.cloudflare.steamstatic.com/store_item_assets/steam/apps/730/header.jpg",
                "https://cdn.cloudflare.steamstatic.com/steam/apps/730/header.jpg", "https://steamcdn-a.akamaihd.net/steam/apps/730/header.jpg",
                "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg", "https://i.vimeocdn.com/video/1.jpg"));
    }

    @Test void rejectsPathTraversalEncodingAndNonImageFilesForImages() {
        rejects(DeveloperMediaType.IMAGE, List.of(
                "https://cdn.akamai.steamstatic.com/steam/apps/../../steamcommunity/a.jpg", "https://cdn.akamai.steamstatic.com/steam/apps/./10/a.jpg",
                "https://cdn.akamai.steamstatic.com/steam/apps/%2e%2e/a.jpg", "https://cdn.akamai.steamstatic.com/steam/apps/10%2fa.jpg",
                "https://cdn.akamai.steamstatic.com/steam/apps//10/a.jpg", "https://cdn.akamai.steamstatic.com/steam/apps/10/a.jpg;x=1",
                "https://cdn.akamai.steamstatic.com/steam/apps/10/a.svg", "https://cdn.akamai.steamstatic.com/steam/apps/10/a.html",
                "https://cdn.akamai.steamstatic.com/steam/apps/10/a", "https://cdn.akamai.steamstatic.com/steam/apps/10/a.jpg.exe",
                "https://CDN.akamai.steamstatic.com.evil.example/steam/apps/10/a.jpg", "https://cdn.akamai.steamstatic.com/Steam/Apps/10/a.jpg"));
        // Uzantı büyük/küçük harf duyarsızdır; sorgu dizesi uzantı kontrolünü etkilemez.
        accepts(DeveloperMediaType.IMAGE, "https://cdn.akamai.steamstatic.com/steam/apps/10/A.JPG?t=1", "https://cdn.akamai.steamstatic.com/steam/apps/10/a.webp");
    }

    @Test void hostListsAreSeparatePerMediaTypeAndEmptyListsRejectEverything() {
        rejects(DeveloperMediaType.IMAGE, List.of("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        rejects(DeveloperMediaType.VIDEO, List.of("https://cdn.akamai.steamstatic.com/steam/apps/10/a.jpg"));
        rejects(DeveloperMediaType.DEMO, List.of("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        var empty = new MediaUrlPolicy(new MediaUrlProperties(null, Set.of(" "), Set.of()));
        for (var type : DeveloperMediaType.values())
            assertThrows(InvalidGameRequestException.class, () -> empty.validate(type, "https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
    }

    @Test void whollyAllowedHostEntriesStillRequireExactHostMatch() {
        var wide = new MediaUrlPolicy(new MediaUrlProperties(Set.of("cdn.example.com"), Set.of(), Set.of()));
        assertNotNull(wide.validate(DeveloperMediaType.IMAGE, "https://cdn.example.com/any/path/a.png"));
        assertThrows(InvalidGameRequestException.class, () -> wide.validate(DeveloperMediaType.IMAGE, "https://evil.cdn.example.com/a.png"));
        assertThrows(InvalidGameRequestException.class, () -> wide.validate(DeveloperMediaType.IMAGE, "https://cdn.example.com.evil.example/a.png"));
    }

    @Test void misconfiguredSourcesFailFastInsteadOfWideningTheAllowlist() {
        for (String bad : List.of("*.steamstatic.com", "https://youtu.be/", "youtu.be:8443/", "user@youtu.be/", "localhost", "youtu.be/../x", "youtu.be//x",
                "youtu.be/%2e", "youtu.be/x?y=1", "youtu.be/x#f", "/watch"))
            assertMisconfigured(Set.of(bad), bad);
    }

    private static void assertMisconfigured(Set<String> sources, String message) {
        assertThrows(IllegalStateException.class, () -> new MediaUrlProperties(sources, Set.of(), Set.of()), message);
    }

    @Test void realApplicationPropertiesBindToTheSameSourcesTheTestsAssume() {
        // Varsayılan yapılandırma dosyasındaki anahtar adlarının ve biçiminin çalıştığını gösterir.
        var source = new MapConfigurationPropertySource(java.util.Map.of(
                "ltz.game.media.image-sources", "Shared.Akamai.Steamstatic.com/store_item_assets/steam/apps/, ",
                "ltz.game.media.demo-sources", "store.steampowered.com/app/"));
        var bound = new Binder(source).bind("ltz.game.media", MediaUrlProperties.class).get();
        assertEquals(Set.of("shared.akamai.steamstatic.com/store_item_assets/steam/apps/"), bound.imageSources());
        assertTrue(bound.videoSources().isEmpty());
        assertEquals(Set.of("store.steampowered.com/app/"), bound.demoSources());
    }
}
