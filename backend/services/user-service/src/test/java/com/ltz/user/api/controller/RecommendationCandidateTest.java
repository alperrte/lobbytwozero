package com.ltz.user.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.user.IntegrationTestSupport;
import com.nimbusds.jwt.JWTClaimsSet;

/**
 * Öneri aday keşfi ({@code GET /internal/v1/accounts/{accountId}/recommendation-candidates}, REC-13). Social engel
 * yansıması güvenilir ilan edilmiştir; güvenilmezken davranış {@link RecommendationCandidateFailClosedTest} içindedir.
 */
@TestPropertySource(properties = "ltz.user.social.projection-enabled=true")
class RecommendationCandidateTest extends IntegrationTestSupport {

    private static final String RECOMMENDATION = "svc:recommendation-service";
    private static final String SCOPE = "candidates:read";

    /** Test sınıfları aynı veritabanını paylaşır; önceki testlerin rızaları aday havuzunu kirletmesin. */
    @BeforeEach
    void clearConsents() {
        jdbc.update("delete from user_consents");
    }

    // ------------------------------------------------------------ uygunluk

    @Test
    void uygunAdayYalnizTercihSinyalleriniDondurur() throws Exception {
        UUID viewer = eligible("rpg", "pc");
        UUID candidate = eligible("rpg", "pc");

        candidates(viewer, "")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.candidates.length()").value(1))
                .andExpect(jsonPath("$.candidates[0].userId").value(candidate.toString()))
                .andExpect(jsonPath("$.candidates[0].favoriteGenres[0]").value("rpg"))
                .andExpect(jsonPath("$.candidates[0].favoritePlatforms[0]").value("pc"))
                .andExpect(jsonPath("$.candidates[0].playerTypes[0]").value("CASUAL"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void yanitYalnizIzinliAlanlariIcerir() throws Exception {
        UUID viewer = eligible("rpg", "pc");
        eligible("rpg", "pc");

        String json = candidates(viewer, "").andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("displayName", "bio", "country", "email", "username", "avatar");
    }

    @Test
    void kendisiRizasizOnboardingsizAskidaVeSilinmisAdaylarDonmez() throws Exception {
        UUID viewer = eligible("rpg", "pc");
        UUID noConsent = profile("ACTIVE", true);
        consent(noConsent, false);
        UUID noOnboarding = profile("ACTIVE", false);
        consent(noOnboarding, true);
        for (String status : List.of("SUSPENDED", "BANNED", "DISABLED", "DELETION_REQUESTED", "DELETED", "PENDING_VERIFICATION")) {
            UUID inactive = profile(status, true);
            consent(inactive, true);
        }

        candidates(viewer, "").andExpect(status().isOk()).andExpect(jsonPath("$.candidates.length()").value(0));
    }

    @Test
    void izleyiciUygunDegilseBosSayfaDonerHataVermez() throws Exception {
        eligible("rpg", "pc");
        UUID noConsent = profile("ACTIVE", true);
        consent(noConsent, false);
        UUID suspended = profile("SUSPENDED", true);
        consent(suspended, true);
        UUID noOnboarding = profile("ACTIVE", false);
        consent(noOnboarding, true);
        UUID unknown = newUser();

        for (UUID viewer : List.of(noConsent, suspended, noOnboarding, unknown)) {
            candidates(viewer, "").andExpect(status().isOk()).andExpect(jsonPath("$.candidates.length()").value(0));
        }
    }

    @Test
    void engelHerIkiYondeAdayiGizler() throws Exception {
        UUID viewer = eligible("rpg", "pc");
        UUID blockedByViewer = eligible("rpg", "pc");
        UUID blocker = eligible("rpg", "pc");
        UUID visible = eligible("rpg", "pc");
        block(viewer, blockedByViewer);
        block(blocker, viewer);
        // Pasif (kalkmış) engel adayı gizlemez.
        jdbc.update("update social_blocks set active = false where blocker_id = ?", blocker);

        candidates(viewer, "").andExpect(jsonPath("$.candidates.length()").value(2))
                .andExpect(jsonPath("$.candidates[?(@.userId=='%s')]".formatted(blockedByViewer)).doesNotExist())
                .andExpect(jsonPath("$.candidates[?(@.userId=='%s')]".formatted(visible)).exists())
                .andExpect(jsonPath("$.candidates[?(@.userId=='%s')]".formatted(blocker)).exists());
    }

    @Test
    void excludeFriendsAktifArkadaslariHerIkiYondeGizler() throws Exception {
        UUID viewer = eligible("rpg", "pc");
        UUID friendA = eligible("rpg", "pc");
        UUID friendB = eligible("rpg", "pc");
        UUID formerFriend = eligible("rpg", "pc");
        UUID stranger = eligible("rpg", "pc");
        // Satır yönü kimlik sırasına göre değişir; iki yön de aynı sonucu vermeli.
        befriend(lower(viewer, friendA), higher(viewer, friendA));
        befriend(lower(viewer, friendB), higher(viewer, friendB));
        befriend(lower(viewer, formerFriend), higher(viewer, formerFriend));
        jdbc.update("update social_friendships set active = false where user_low_id = ? or user_high_id = ?",
                formerFriend, formerFriend);

        // Varsayılan: arkadaşlar dışlanmaz (geriye uyumlu).
        candidates(viewer, "").andExpect(jsonPath("$.candidates.length()").value(4));
        candidates(viewer, "?excludeFriends=false").andExpect(jsonPath("$.candidates.length()").value(4));
        candidates(viewer, "?excludeFriends=true").andExpect(jsonPath("$.candidates.length()").value(2))
                .andExpect(jsonPath("$.candidates[?(@.userId=='%s')]".formatted(friendA)).doesNotExist())
                .andExpect(jsonPath("$.candidates[?(@.userId=='%s')]".formatted(friendB)).doesNotExist())
                .andExpect(jsonPath("$.candidates[?(@.userId=='%s')]".formatted(formerFriend)).exists())
                .andExpect(jsonPath("$.candidates[?(@.userId=='%s')]".formatted(stranger)).exists());
    }

    @Test
    void excludeFriendsGecersizDegerIstekReddedilir() throws Exception {
        UUID viewer = eligible("rpg", "pc");
        candidates(viewer, "?excludeFriends=maybe").andExpect(status().isBadRequest());
    }

    private static UUID lower(UUID first, UUID second) {
        return first.toString().compareTo(second.toString()) < 0 ? first : second;
    }

    private static UUID higher(UUID first, UUID second) {
        return lower(first, second).equals(first) ? second : first;
    }

    // ------------------------------------------------------------ süzgeç ve sayfalama

    @Test
    void turVePlatformSuzgeciSunucudaUygulanir() throws Exception {
        UUID viewer = eligible("rpg", "pc");
        UUID rpgPc = eligible("rpg", "pc");
        UUID rpgConsole = eligible("rpg", "console");
        eligible("racing", "pc");

        candidates(viewer, "?genre=rpg&platform=pc").andExpect(jsonPath("$.candidates.length()").value(1))
                .andExpect(jsonPath("$.candidates[0].userId").value(rpgPc.toString()));
        candidates(viewer, "?genre=RPG&platform=console").andExpect(jsonPath("$.candidates.length()").value(1))
                .andExpect(jsonPath("$.candidates[0].userId").value(rpgConsole.toString()));
        candidates(viewer, "?genre=rpg").andExpect(jsonPath("$.candidates.length()").value(2));
        candidates(viewer, "?platform=pc").andExpect(jsonPath("$.candidates.length()").value(2));
    }

    @Test
    void sayfalamaDeterministikVeEksiksizdir() throws Exception {
        UUID viewer = eligible("rpg", "pc");
        List<UUID> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(eligible("rpg", "pc"));
        }
        // PostgreSQL uuid'yi bayt sırasıyla sıralar (küçük harfli hex metin sırasıyla aynı); Java UUID.compareTo işaretlidir.
        List<UUID> sorted = created.stream().sorted(java.util.Comparator.comparing(UUID::toString)).toList();

        List<UUID> seen = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 3; page++) {
            String query = "?limit=2" + (cursor == null ? "" : "&cursor=" + cursor);
            String json = candidates(viewer, query).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            com.jayway.jsonpath.DocumentContext context = com.jayway.jsonpath.JsonPath.parse(json);
            List<String> ids = context.read("$.candidates[*].userId");
            ids.forEach(id -> seen.add(UUID.fromString(id)));
            cursor = context.read("$.nextCursor");
            if (page < 2) {
                assertThat(cursor).isNotNull();
            }
        }
        assertThat(cursor).isNull();
        assertThat(seen).containsExactlyElementsOf(sorted);
    }

    @Test
    void limitVeImlecDogrulanir() throws Exception {
        UUID viewer = eligible("rpg", "pc");
        for (String query : List.of("?limit=0", "?limit=51", "?limit=-1", "?limit=abc", "?cursor=not-a-uuid",
                "?genre=%27%3B--", "?platform=a%20b", "?genre=" + "a".repeat(49))) {
            candidates(viewer, query).andExpect(status().isBadRequest());
        }
        candidates(viewer, "?limit=50").andExpect(status().isOk());
        candidates(viewer, "?limit=1").andExpect(status().isOk());
    }

    // ------------------------------------------------------------ kimlik ve yetki

    @Test
    void yanlisServisKapsamVeHesapBagi403() throws Exception {
        UUID viewer = eligible("rpg", "pc");

        request(viewer, "", token("svc:hardware-service", SCOPE, viewer)).andExpect(status().isForbidden());
        request(viewer, "", token("svc:social-service", SCOPE, viewer)).andExpect(status().isForbidden());
        request(viewer, "", token("svc:auth-service", SCOPE, viewer)).andExpect(status().isForbidden());
        request(viewer, "", token(RECOMMENDATION, "privacy:read", viewer)).andExpect(status().isForbidden());
        request(viewer, "", token(RECOMMENDATION, "events:ingest", viewer)).andExpect(status().isForbidden());
        // Token başka hesaba bağlı ya da hesaba hiç bağlı değil: izleyici kimliği taklit edilemez.
        request(viewer, "", token(RECOMMENDATION, SCOPE, newUser())).andExpect(status().isForbidden());
        request(viewer, "", serviceToken(RECOMMENDATION, SCOPE, UnaryOperator.identity())).andExpect(status().isForbidden());
    }

    @Test
    void kullaniciTokeniTokensizVeYanlisYontem() throws Exception {
        UUID viewer = eligible("rpg", "pc");
        String path = InternalApiPaths.RECOMMENDATION_CANDIDATES.replace("{accountId}", viewer.toString());

        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(viewer))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION,
                "Bearer " + token(RECOMMENDATION, SCOPE, viewer))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ yardımcılar

    /** ACTIVE, onboarding'i bitmiş, öneri rızası açık, verilen tür ve platformu seven oyuncu. */
    private UUID eligible(String genre, String platform) {
        UUID id = profile("ACTIVE", true);
        jdbc.update("update user_profiles set favorite_genres = ?::text[], favorite_platforms = ?::text[] where user_id = ?",
                "{" + genre + "}", "{" + platform + "}", id);
        consent(id, true);
        return id;
    }

    private UUID profile(String status, boolean onboarded) {
        UUID id = newUser();
        insertNamedProfile(id, "u" + PublicProfileControllerTest.suffix(), "Ad", status);
        if (onboarded) {
            jdbc.update("update user_profiles set onboarding_completed_at = now() where user_id = ?", id);
        }
        return id;
    }

    private void consent(UUID id, boolean granted) {
        jdbc.update("""
                insert into user_consents (user_id, recommendation_granted, recommendation_changed_at, updated_at, version)
                values (?, ?, now(), now(), 0)""", id, granted);
    }

    private ResultActions candidates(UUID viewer, String query) throws Exception {
        return request(viewer, query, token(RECOMMENDATION, SCOPE, viewer));
    }

    private ResultActions request(UUID viewer, String query, String token) throws Exception {
        return mockMvc.perform(get(InternalApiPaths.RECOMMENDATION_CANDIDATES.replace("{accountId}", viewer.toString())
                + query).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static String token(String subject, String scope, UUID accountId) {
        return serviceToken(subject, scope, claims -> claims.claim("account_id", accountId.toString()));
    }

    private static String serviceToken(String subject, String scope, UnaryOperator<JWTClaimsSet.Builder> change) {
        return sign(SIGNING_KEY, SERVICE_TOKEN_TYPE, change.apply(serviceClaims(subject, scope)).build());
    }
}
