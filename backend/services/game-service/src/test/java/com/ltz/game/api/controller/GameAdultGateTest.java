package com.ltz.game.api.controller;

import com.ltz.game.AuthenticatedApiTest;
import com.ltz.game.application.service.CatalogImportService;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * +18 kapısı: {@code includeAdult=true} yalnız Auth'un imzaladığı access token'daki boolean {@code adult_verified=true} iddiasıyla uygulanır. Anonim, iddiasız,
 * {@code false} ve yanlış türde iddia sessizce yok sayılır ({@code includeAdultApplied=false}); sahte veya süresi dolmuş token {@code 401} alır. Yalnız
 * {@code /games} ve {@code /games/search} kapıdadır; popüler liste, detay ve {@code batch} değişmez.
 */
class GameAdultGateTest extends AuthenticatedApiTest {
    static final String CLAIM = "adult_verified";
    UUID plain, adult, adultDelisted;

    @BeforeEach void seed() {
        jdbc.update("delete from game_external_refs");
        jdbc.update("delete from games");
        plain = game("Plain");
        adult = game("Adult game");
        adultDelisted = game("Adult gone");
        jdbc.update("update games set adult=false where id=?", plain);
        jdbc.update("update games set adult=true where id in (?,?)", adult, adultDelisted);
        jdbc.update("update games set delisted_at=now() where id=?", adultDelisted);
        jdbc.update("update games set editor_rank=1 where id=?", plain);
    }

    UUID game(String title) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,created_at,updated_at,origin,game_type,status,metadata_completeness) values (?,?,?,now(),now(),'EXTERNAL','MAIN_GAME','ACTIVE','INDEX_ONLY')",
                id, title, CatalogImportService.normalize(title));
        return id;
    }

    static String userWith(Object claim) { return sign(claims(UUID.randomUUID(), List.of("USER")).claim(CLAIM, claim), KEYS.getPrivate()); }

    static String userWithoutClaim() { return sign(claims(UUID.randomUUID(), List.of("USER")), KEYS.getPrivate()); }

    ResultActions list(String path, String token, String... params) throws Exception {
        var request = get(path);
        for (int index = 0; index < params.length; index += 2) request.param(params[index], params[index + 1]);
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mvc.perform(request);
    }

    @Test void anonymousIncludeAdultIsSilentlyIgnoredWithoutAnError() throws Exception {
        list("/api/v1/game/games", null, "includeAdult", "true").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.includeAdultApplied").value(false))
                .andExpect(jsonPath("$.items[?(@.game.title=='Adult game')]").isEmpty());
        list("/api/v1/game/games/search", null, "q", "Adult", "includeAdult", "true").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.includeAdultApplied").value(false));
    }

    @Test void onlyTheBooleanTrueClaimGrantsAccessEveryOtherTokenIsTreatedAsNotVerified() throws Exception {
        Map<String, String> tokens = Map.of("claim yok", userWithoutClaim(), "false", userWith(false), "metin true", userWith("true"),
                "sayı 1", userWith(1), "liste", userWith(List.of(true)), "metin evet", userWith("yes"));
        for (var entry : tokens.entrySet())
            list("/api/v1/game/games", entry.getValue(), "includeAdult", "true").andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.includeAdultApplied").value(false))
                    .andExpect(jsonPath("$.items[?(@.game.title=='Adult game')]").isEmpty());
    }

    @Test void aVerifiedUserWhoAsksForAdultContentGetsItButDelistedGamesStayHidden() throws Exception {
        list("/api/v1/game/games", userWith(true), "includeAdult", "true").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.includeAdultApplied").value(true))
                .andExpect(jsonPath("$.items[?(@.game.title=='Adult game')].adult").value(true))
                .andExpect(jsonPath("$.items[?(@.game.title=='Adult gone')]").isEmpty());
    }

    @Test void aVerifiedUserWhoDoesNotAskStillSeesNoAdultContentAndSearchCountsAreConsistent() throws Exception {
        list("/api/v1/game/games", userWith(true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.includeAdultApplied").value(false));
        list("/api/v1/game/games/search", userWith(true), "q", "Adult", "includeAdult", "true").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.includeAdultApplied").value(true))
                .andExpect(jsonPath("$.items[0].game.title").value("Adult game"));
        list("/api/v1/game/games/search", userWith(true), "q", "Adult").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.includeAdultApplied").value(false));
    }

    @Test void thePopularListIsNotBehindTheGateAndStaysAdultFree() throws Exception {
        list("/api/v1/game/games/popular", userWith(true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.includeAdultApplied").value(false)).andExpect(jsonPath("$.items[?(@.game.title=='Adult game')]").isEmpty());
    }

    @Test void aForgedOrExpiredTokenIsRejectedEvenWhenItCarriesTheClaim() throws Exception {
        String forged = sign(claims(UUID.randomUUID(), List.of("USER")).claim(CLAIM, true), OTHER_KEYS.getPrivate());
        list("/api/v1/game/games", forged, "includeAdult", "true").andExpect(status().isUnauthorized());
        String expired = sign(claims(UUID.randomUUID(), List.of("USER")).claim(CLAIM, true)
                .issueTime(Date.from(Instant.now().minusSeconds(900))).notBeforeTime(Date.from(Instant.now().minusSeconds(900)))
                .expirationTime(Date.from(Instant.now().minusSeconds(600))), KEYS.getPrivate());
        list("/api/v1/game/games", expired, "includeAdult", "true").andExpect(status().isUnauthorized());
    }

    @Test void responsesThatAskForAdultContentAreNeverSharedBetweenUsers() throws Exception {
        for (String token : new String[]{null, userWith(true)})
            list("/api/v1/game/games", token, "includeAdult", "true").andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", containsString("no-store"))).andExpect(header().string("Cache-Control", containsString("private")))
                    .andExpect(header().string("Vary", containsString("Authorization"))).andExpect(header().string("Vary", containsString("Cookie")));
    }

    @Test void theGateDoesNotTouchTheDetailEndpointOrADirectLink() throws Exception {
        list("/api/v1/game/games/" + adult, null).andExpect(status().isOk()).andExpect(jsonPath("$.metadata.adult").value(true));
        list("/api/v1/game/games/" + adult, userWith(true)).andExpect(status().isOk()).andExpect(jsonPath("$.metadata.adult").value(true));
    }
}
