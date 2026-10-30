package com.ltz.user.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.user.IntegrationTestSupport;

/** Oyuncu arama (USER-11) ve toplu profil kartı. Her test benzersiz bir önek kullanır (ortak veritabanı). */
class PlayerSearchTest extends IntegrationTestSupport {

    // ------------------------------------------------------------ arama

    @Test
    void kullaniciAdiVeGorunenAdOnekiyleBuyukKucukHarfeDuyarsizBulur() throws Exception {
        String prefix = "s" + PublicProfileControllerTest.suffix();
        UUID byUsername = newUser();
        UUID byDisplayName = newUser();
        insertNamedProfile(byUsername, prefix + "_one", "Baska Ad", "ACTIVE");
        insertNamedProfile(byDisplayName, "z" + PublicProfileControllerTest.suffix(), prefix.toUpperCase() + " Isim",
                "ACTIVE");
        insertNamedProfile(newUser(), "x" + PublicProfileControllerTest.suffix(), "Alakasiz", "ACTIVE");

        search(newUser(), prefix.toUpperCase()).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[*].userId", Matchers.containsInAnyOrder(byUsername.toString(),
                        byDisplayName.toString())))
                .andExpect(jsonPath("$.items[0].bio").doesNotExist());
    }

    @Test
    void turkceKarakterliGorunenAdBulunur() throws Exception {
        String unique = PublicProfileControllerTest.suffix();
        UUID owner = newUser();
        insertNamedProfile(owner, "t" + unique, "x", "ACTIVE");
        // Görünen adı uygulama yoluyla yaz: normalize sütun Java tarafında üretilir.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(UserApiPaths.ME_PROFILE)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"displayName\":\"Çağrı Öz%s\"}".formatted(unique))).andExpect(status().isOk());

        Locale turkish = Locale.forLanguageTag("tr");
        // Türkçe i katlaması: ı/I/İ/i aynı anahtara düşer; makinenin yerelinden bağımsızdır.
        for (String query : List.of("çağrı öz" + unique, "ÇAĞRI ÖZ" + unique.toUpperCase(turkish),
                "çağri öz" + unique, "Çağrı Öz" + unique.toUpperCase(Locale.ROOT))) {
            search(newUser(), query).andExpect(jsonPath("$.items[0].userId").value(owner.toString()));
        }
    }

    @Test
    void askidakiYasakliVeEngelliHesaplarCikmaz() throws Exception {
        String prefix = "h" + PublicProfileControllerTest.suffix();
        UUID viewer = newUser();
        UUID active = newUser();
        insertNamedProfile(active, prefix + "a", "Ad", "ACTIVE");
        insertNamedProfile(newUser(), prefix + "b", "Ad", "SUSPENDED");
        insertNamedProfile(newUser(), prefix + "c", "Ad", "BANNED");
        UUID blockedByViewer = newUser();
        UUID blockingViewer = newUser();
        insertNamedProfile(blockedByViewer, prefix + "d", "Ad", "ACTIVE");
        insertNamedProfile(blockingViewer, prefix + "e", "Ad", "ACTIVE");
        block(viewer, blockedByViewer);
        block(blockingViewer, viewer);

        search(viewer, prefix).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].userId").value(active.toString()));
    }

    @Test
    void enCokYirmiSonucDoner() throws Exception {
        String prefix = "l" + PublicProfileControllerTest.suffix();
        for (int i = 0; i < 25; i++) {
            insertNamedProfile(newUser(), prefix + String.format("%02d", i), "Ad", "ACTIVE");
        }

        search(newUser(), prefix).andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.items[0].username").value(prefix + "00"));
    }

    @Test
    void likeOzelKarakterleriJokerSayilmaz() throws Exception {
        String unique = PublicProfileControllerTest.suffix();
        insertNamedProfile(newUser(), "w" + unique, "Ad", "ACTIVE");

        search(newUser(), "%" + unique).andExpect(jsonPath("$.items.length()").value(0));
        search(newUser(), "w_" + unique.substring(1)).andExpect(jsonPath("$.items.length()").value(0));
        search(newUser(), "\\" + unique).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void gecersizSorgu400() throws Exception {
        UUID viewer = newUser();

        search(viewer, "a").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));
        search(viewer, "   ").andExpect(status().isBadRequest());
        search(viewer, "a".repeat(33)).andExpect(status().isBadRequest());
        search(viewer, "ab‮cd").andExpect(status().isBadRequest());
        mockMvc.perform(get(UserApiPaths.SEARCH).header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void misafirAramaYapamaz401() throws Exception {
        mockMvc.perform(get(UserApiPaths.SEARCH).param("q", "abc")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ toplu kart

    @Test
    void topluKartIstekSirasiniKorurGizlenenleriAtlar() throws Exception {
        UUID viewer = newUser();
        UUID first = newUser();
        UUID second = newUser();
        UUID banned = newUser();
        UUID blocked = newUser();
        insertNamedProfile(first, "f" + PublicProfileControllerTest.suffix(), "Bir", "ACTIVE");
        insertNamedProfile(second, "g" + PublicProfileControllerTest.suffix(), "Iki", "SUSPENDED");
        insertNamedProfile(banned, "b" + PublicProfileControllerTest.suffix(), "Uc", "BANNED");
        insertNamedProfile(blocked, "k" + PublicProfileControllerTest.suffix(), "Dort", "ACTIVE");
        block(blocked, viewer);

        batch(viewer, List.of(second, UUID.randomUUID(), banned, first, blocked, second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].userId").value(second.toString()))
                .andExpect(jsonPath("$.items[1].userId").value(first.toString()))
                .andExpect(jsonPath("$.items[1].displayName").value("Bir"))
                .andExpect(jsonPath("$.items[1].avatar").doesNotExist());
    }

    @Test
    void topluKartSinirlari() throws Exception {
        UUID viewer = newUser();
        List<UUID> tooMany = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            tooMany.add(UUID.randomUUID());
        }

        batch(viewer, tooMany).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        batch(viewer, List.of()).andExpect(status().isBadRequest());
        batch(viewer, tooMany.subList(0, 50)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(post(UserApiPaths.PROFILES_BATCH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\":[\"%s\"]}".formatted(UUID.randomUUID()))).andExpect(status().isUnauthorized());
    }

    private ResultActions search(UUID viewer, String q) throws Exception {
        return mockMvc.perform(get(UserApiPaths.SEARCH).param("q", q).header(HttpHeaders.AUTHORIZATION, bearer(viewer)));
    }

    private ResultActions batch(UUID viewer, List<UUID> ids) throws Exception {
        String body = ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",", "{\"userIds\":[", "]}"));
        return mockMvc.perform(post(UserApiPaths.PROFILES_BATCH).header(HttpHeaders.AUTHORIZATION, bearer(viewer))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
