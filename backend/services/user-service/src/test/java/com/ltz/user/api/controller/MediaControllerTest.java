package com.ltz.user.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.jayway.jsonpath.JsonPath;
import com.ltz.user.IntegrationTestSupport;
import com.ltz.user.TestImages;
import com.ltz.user.application.port.MediaStoragePort;

import jakarta.servlet.http.Cookie;

/** Profil görselleri (USER-03 avatar, USER-04 kapak): yükleme, dönüştürme, sunma, değiştirme, kaldırma, sınırlar. */
class MediaControllerTest extends IntegrationTestSupport {

    @Autowired
    private MediaStoragePort storage;

    // ------------------------------------------------------------ avatar dönüştürme

    @Test
    void avatarPngKareyeKirpilirVeIkiBoyutluSunulur() throws Exception {
        UUID user = newUser();

        String body = put(UserApiPaths.ME_AVATAR, user, "image/png", TestImages.png(1000, 800))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.avatar.animated").value(false))
                .andExpect(jsonPath("$.cover").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String large = JsonPath.read(body, "$.avatar.large");
        String small = JsonPath.read(body, "$.avatar.small");
        assertThat(large).startsWith(UserApiPaths.MEDIA + "/").endsWith(".jpg");
        assertThat(small).contains("-s.");
        BufferedImage largeImage = fetchImage(large, "image/jpeg");
        BufferedImage smallImage = fetchImage(small, "image/jpeg");
        assertThat(largeImage.getWidth()).isEqualTo(512);
        assertThat(largeImage.getHeight()).isEqualTo(512);
        assertThat(smallImage.getWidth()).isEqualTo(128);
        assertThat(smallImage.getHeight()).isEqualTo(128);
    }

    @Test
    void kucukGorselBuyutulmez() throws Exception {
        UUID user = newUser();

        String body = put(UserApiPaths.ME_AVATAR, user, "image/jpeg", TestImages.jpeg(100, 100))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        BufferedImage large = fetchImage(JsonPath.read(body, "$.avatar.large"), "image/jpeg");
        assertThat(large.getWidth()).isEqualTo(100);
        assertThat(large.getHeight()).isEqualTo(100);
    }

    @Test
    void saydamPngPngKalirOpakAlfaliPngJpegOlur() throws Exception {
        String transparent = put(UserApiPaths.ME_AVATAR, newUser(), "image/png", TestImages.transparentPng(300, 300))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String opaque = put(UserApiPaths.ME_AVATAR, newUser(), "image/png", TestImages.opaqueAlphaPng(300, 300))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(transparent, "$.avatar.large")).endsWith(".png");
        fetchImage(JsonPath.read(transparent, "$.avatar.large"), "image/png");
        assertThat((String) JsonPath.read(opaque, "$.avatar.large")).endsWith(".jpg");
    }

    // ------------------------------------------------------------ kapak

    @Test
    void kapakEnBoyOraniniKorurVeSinirlaraSigdirilir() throws Exception {
        UUID user = newUser();

        String body = put(UserApiPaths.ME_COVER, user, "image/jpeg", TestImages.jpeg(4000, 1000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        BufferedImage large = fetchImage(JsonPath.read(body, "$.cover.large"), "image/jpeg");
        BufferedImage small = fetchImage(JsonPath.read(body, "$.cover.small"), "image/jpeg");
        assertThat(large.getWidth()).isEqualTo(1600);
        assertThat(large.getHeight()).isEqualTo(400);
        assertThat(small.getWidth()).isEqualTo(800);
        assertThat(small.getHeight()).isEqualTo(200);
    }

    @Test
    void exifYonlendirmesiUygulanirTelefonFotografiYanDonukGorunmez() throws Exception {
        UUID user = newUser();

        // 600x300 yatay, EXIF=6 (90° saat yönü): gerçek görüntü 300x600 dikey.
        String body = put(UserApiPaths.ME_COVER, user, "image/jpeg", TestImages.jpegWithOrientation(600, 300, 6))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        BufferedImage large = fetchImage(JsonPath.read(body, "$.cover.large"), "image/jpeg");
        assertThat(large.getWidth()).isEqualTo(300);
        assertThat(large.getHeight()).isEqualTo(600);
    }

    // ------------------------------------------------------------ GIF

    @Test
    void gifYenidenKodlanmazAmaTemizlenirYorumGiderDongKalir() throws Exception {
        UUID user = newUser();
        byte[] gif = TestImages.gif(2, true, 64, 64);

        String body = put(UserApiPaths.ME_AVATAR, user, "image/gif", gif)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar.animated").value(true))
                .andReturn().getResponse().getContentAsString();

        String large = JsonPath.read(body, "$.avatar.large");
        assertThat((String) JsonPath.read(body, "$.avatar.small")).isEqualTo(large);
        byte[] served = get(large).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/gif"))
                .andReturn().getResponse().getContentAsByteArray();
        String text = new String(served, StandardCharsets.ISO_8859_1);
        assertThat(text).contains("NETSCAPE2.0").doesNotContain("hello");
        assertThat(served.length).isLessThan(gif.length);
    }

    @Test
    void gifKareSayisiVeEkranBoyutuSiniri() throws Exception {
        UUID user = newUser();

        put(UserApiPaths.ME_AVATAR, user, "image/gif", TestImages.gif(101, false, 64, 64))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
        put(UserApiPaths.ME_AVATAR, user, "image/gif", TestImages.gif(1, false, 600, 600))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE_DIMENSIONS"));
        // Kapakta 1600'e kadar serbest.
        put(UserApiPaths.ME_COVER, user, "image/gif", TestImages.gif(1, false, 1500, 300)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ reddedilenler

    @Test
    void svgHtmlMetinVeUyumsuzIcerikReddedilir() throws Exception {
        UUID user = newUser();
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        byte[] html = "<html><body>hi</body></html>".getBytes(StandardCharsets.UTF_8);

        put(UserApiPaths.ME_AVATAR, user, "image/png", svg).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
        put(UserApiPaths.ME_AVATAR, user, "image/jpeg", html).andExpect(status().isBadRequest());
        // Bildirilen tür gerçek içerikle uyuşmuyor (PNG içeriği JPEG diye).
        put(UserApiPaths.ME_AVATAR, user, "image/jpeg", TestImages.png(64, 64)).andExpect(status().isBadRequest());
        put(UserApiPaths.ME_AVATAR, user, "image/gif", TestImages.jpeg(64, 64)).andExpect(status().isBadRequest());
        // Başlığı doğru ama gövdesi bozuk.
        byte[] corruptedJpeg = TestImages.jpeg(200, 200);
        for (int i = 200; i < corruptedJpeg.length; i++) {
            corruptedJpeg[i] = 0;
        }
        put(UserApiPaths.ME_AVATAR, user, "image/jpeg", corruptedJpeg).andExpect(status().isBadRequest());
        put(UserApiPaths.ME_AVATAR, user, "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})
                .andExpect(status().isBadRequest());
        // Gif imzasıyla başlayıp HTML taşıyan çok biçimli (polyglot) dosya.
        put(UserApiPaths.ME_AVATAR, user, "image/gif", "GIF89a<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest());

        assertThat(currentRows(user)).isZero();
    }

    @Test
    void asiriBuyukBoyutVePikselSayisiReddedilir() throws Exception {
        UUID user = newUser();

        put(UserApiPaths.ME_COVER, user, "image/png", TestImages.monochromePng(8001, 1))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE_DIMENSIONS"));
        // 42 milyon piksel ama birkaç KB: dekompresyon bombası.
        put(UserApiPaths.ME_COVER, user, "image/png", TestImages.monochromePng(7000, 6000))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE_DIMENSIONS"));
    }

    @Test
    void desteklenmeyenIcerikTuru415BosGovde400() throws Exception {
        UUID user = newUser();

        put(UserApiPaths.ME_AVATAR, user, "text/plain", "x".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnsupportedMediaType());
        put(UserApiPaths.ME_AVATAR, user, "image/svg+xml", "<svg/>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnsupportedMediaType());
        put(UserApiPaths.ME_AVATAR, user, "image/png", new byte[0]).andExpect(status().isBadRequest());
    }

    @Test
    void tureGoreBaytSiniri413() throws Exception {
        UUID user = newUser();
        byte[] justOverAvatarLimit = new byte[2 * 1024 * 1024 + 1];
        byte[] justOverCoverLimit = new byte[4 * 1024 * 1024 + 1];

        put(UserApiPaths.ME_AVATAR, user, "image/png", justOverAvatarLimit)
                .andExpect(status().isContentTooLarge()).andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
        put(UserApiPaths.ME_COVER, user, "image/png", justOverCoverLimit).andExpect(status().isContentTooLarge());
        // Avatar sınırının üstü ama kapak sınırının altı: kapakta bayt sınırı geçer (içerik geçersiz olduğu için 400).
        put(UserApiPaths.ME_COVER, user, "image/png", justOverAvatarLimit).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ değiştirme ve kaldırma

    @Test
    void yenidenYuklemeEskiDosyayiSilerYenisiniSunar() throws Exception {
        UUID user = newUser();
        String first = put(UserApiPaths.ME_AVATAR, user, "image/png", TestImages.png(300, 300))
                .andReturn().getResponse().getContentAsString();
        String firstLarge = JsonPath.read(first, "$.avatar.large");
        String firstSmall = JsonPath.read(first, "$.avatar.small");

        String second = put(UserApiPaths.ME_AVATAR, user, "image/png", TestImages.png(400, 400))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String secondLarge = JsonPath.read(second, "$.avatar.large");

        assertThat(secondLarge).isNotEqualTo(firstLarge);
        get(secondLarge).andExpect(status().isOk());
        get(firstLarge).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
        get(firstSmall).andExpect(status().isNotFound());
        assertThat(currentRows(user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from user_media where user_id = ? and status = 'PENDING_DELETE'",
                Integer.class, user)).isEqualTo(1);
        me(user).andExpect(jsonPath("$.avatar.large").value(secondLarge));
    }

    @Test
    void avatarVeKapakBirbirindenBagimsiz() throws Exception {
        UUID user = newUser();
        put(UserApiPaths.ME_AVATAR, user, "image/png", TestImages.png(200, 200)).andExpect(status().isOk());

        put(UserApiPaths.ME_COVER, user, "image/png", TestImages.png(800, 300)).andExpect(status().isOk());

        me(user).andExpect(jsonPath("$.avatar.large").exists()).andExpect(jsonPath("$.cover.large").exists());
        assertThat(currentRows(user)).isEqualTo(2);
    }

    @Test
    void kaldirmaProfildenVeDiskdenSilerIkinciCagriDaBasarili() throws Exception {
        UUID user = newUser();
        String body = put(UserApiPaths.ME_AVATAR, user, "image/png", TestImages.png(300, 300))
                .andReturn().getResponse().getContentAsString();
        String large = JsonPath.read(body, "$.avatar.large");

        remove(UserApiPaths.ME_AVATAR, user).andExpect(status().isNoContent());
        remove(UserApiPaths.ME_AVATAR, user).andExpect(status().isNoContent());

        me(user).andExpect(jsonPath("$.avatar").doesNotExist());
        get(large).andExpect(status().isNotFound());
        assertThat(currentRows(user)).isZero();
    }

    @Test
    void profilYokkenKaldirma204() throws Exception {
        remove(UserApiPaths.ME_COVER, newUser()).andExpect(status().isNoContent());
    }

    @Test
    void herkesYalnizKendiGorseliniDegistirir() throws Exception {
        UUID owner = newUser();
        UUID other = newUser();
        String body = put(UserApiPaths.ME_AVATAR, owner, "image/png", TestImages.png(200, 200))
                .andReturn().getResponse().getContentAsString();
        String ownerLarge = JsonPath.read(body, "$.avatar.large");

        put(UserApiPaths.ME_AVATAR, other, "image/png", TestImages.png(210, 210)).andExpect(status().isOk());
        remove(UserApiPaths.ME_AVATAR, other).andExpect(status().isNoContent());

        get(ownerLarge).andExpect(status().isOk());
        me(owner).andExpect(jsonPath("$.avatar.large").value(ownerLarge));
    }

    @Test
    void eszamanliYuklemelerdeTekGuncelGorselKalir() throws Exception {
        UUID user = newUser();
        Callable<Integer> upload = () -> put(UserApiPaths.ME_AVATAR, user, "image/png", TestImages.png(200, 200))
                .andReturn().getResponse().getStatus();

        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            for (var result : pool.invokeAll(List.of(upload, upload, upload, upload))) {
                assertThat(result.get()).isIn(200, 409);
            }
        }

        assertThat(currentRows(user)).isEqualTo(1);
        String large = JsonPath.read(me(user).andReturn().getResponse().getContentAsString(), "$.avatar.large");
        get(large).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ yükleme sınırı

    @Test
    void saatlikYuklemeSiniri429() throws Exception {
        UUID user = newUser();
        for (int i = 0; i < 10; i++) {
            put(UserApiPaths.ME_AVATAR, user, "image/png", TestImages.png(64, 64)).andExpect(status().isOk());
        }

        put(UserApiPaths.ME_AVATAR, user, "image/png", TestImages.png(64, 64))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("MEDIA_RATE_LIMITED"));
        // Sınır kullanıcıya özeldir.
        put(UserApiPaths.ME_AVATAR, newUser(), "image/png", TestImages.png(64, 64)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ herkese açık sunum

    @Test
    void gorselTokensizOkunurVeDegismezOnbellekBasliklariVardir() throws Exception {
        String body = put(UserApiPaths.ME_AVATAR, newUser(), "image/png", TestImages.png(300, 300))
                .andReturn().getResponse().getContentAsString();

        get(JsonPath.read(body, "$.avatar.large"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline"));
    }

    @Test
    void gecersizVeOlmayanDosyaAdlari404() throws Exception {
        String missing = "0".repeat(32);

        get(UserApiPaths.MEDIA + "/" + missing + ".jpg").andExpect(status().isNotFound());
        get(UserApiPaths.MEDIA + "/" + missing + "-s.png").andExpect(status().isNotFound());
        get(UserApiPaths.MEDIA + "/short.jpg").andExpect(status().isNotFound());
        get(UserApiPaths.MEDIA + "/" + missing.toUpperCase() + ".jpg").andExpect(status().isNotFound());
        get(UserApiPaths.MEDIA + "/" + missing + ".svg").andExpect(status().isNotFound());
        get(UserApiPaths.MEDIA + "/..passwd.jpg").andExpect(status().isNotFound());
        assertThat(storage.open("../../etc/passwd")).isEmpty();
    }

    // ------------------------------------------------------------ kimlik

    @Test
    void yazmaUclariTokenIsterServisVeKullaniciOlmayanTokenReddedilir() throws Exception {
        byte[] image = TestImages.png(64, 64);

        mockMvc.perform(MockMvcRequestBuilders.put(UserApiPaths.ME_AVATAR).contentType(MediaType.IMAGE_PNG)
                .content(image)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(UserApiPaths.ME_AVATAR)).andExpect(status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.put(UserApiPaths.ME_COVER).contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken(newUser(), UnaryOperator.identity()))
                .content(image)).andExpect(status().isUnauthorized());
    }

    @Test
    void cerezleKimlikOkunmaz() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put(UserApiPaths.ME_AVATAR).contentType(MediaType.IMAGE_PNG)
                .cookie(new Cookie("ltz_access", accessToken(newUser(), UnaryOperator.identity())))
                .content(TestImages.png(64, 64))).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ hesap silme

    @Test
    void hesapSilmeTumGorselDosyalariniSilerDegistirilenlerDahil() throws Exception {
        UUID user = newUser();
        String first = put(UserApiPaths.ME_AVATAR, user, "image/png", TestImages.png(300, 300))
                .andReturn().getResponse().getContentAsString();
        String second = put(UserApiPaths.ME_AVATAR, user, "image/png", TestImages.png(310, 310))
                .andReturn().getResponse().getContentAsString();
        String cover = put(UserApiPaths.ME_COVER, user, "image/jpeg", TestImages.jpeg(900, 300))
                .andReturn().getResponse().getContentAsString();
        UUID bystander = newUser();
        String bystanderBody = put(UserApiPaths.ME_AVATAR, bystander, "image/png", TestImages.png(320, 320))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(delete(InternalApiPaths.ACCOUNT_ERASURE, user)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken(user, UnaryOperator.identity())))
                .andExpect(status().isNoContent());

        for (String url : List.of(JsonPath.<String>read(second, "$.avatar.large"), JsonPath.<String>read(second, "$.avatar.small"),
                JsonPath.<String>read(cover, "$.cover.large"), JsonPath.<String>read(cover, "$.cover.small"))) {
            get(url).andExpect(status().isNotFound());
        }
        assertThat(jdbc.queryForObject("select count(*) from user_media where user_id = ?", Integer.class, user)).isZero();
        assertThat(profileCount(user)).isZero();
        assertThat((String) JsonPath.read(first, "$.avatar.large")).isNotEqualTo(JsonPath.read(second, "$.avatar.large"));
        get(JsonPath.read(bystanderBody, "$.avatar.large")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ yardımcılar

    private ResultActions put(String path, UUID user, String contentType, byte[] content) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.put(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .content(content));
    }

    private ResultActions remove(String path, UUID user) throws Exception {
        return mockMvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions me(UUID user) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(UserApiPaths.ME)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions get(String path) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(path));
    }

    private BufferedImage fetchImage(String path, String expectedType) throws Exception {
        MockHttpServletResponse response = get(path).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, expectedType)).andReturn().getResponse();
        BufferedImage image = TestImages.read(response.getContentAsByteArray());
        assertThat(image).isNotNull();
        return image;
    }

    private int currentRows(UUID user) {
        return jdbc.queryForObject("select count(*) from user_media where user_id = ? and status = 'CURRENT'",
                Integer.class, user);
    }
}
