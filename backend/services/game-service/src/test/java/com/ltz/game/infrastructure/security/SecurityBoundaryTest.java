package com.ltz.game.infrastructure.security;

import com.ltz.game.AuthenticatedApiTest;
import com.nimbusds.jwt.PlainJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JWT doğrulama, rol çözümleme ve uç yetkilendirme sınırı. Tüm token'lar bu testte yerel üretilmiştir; bu sınıf
 * gerçek Auth Service entegrasyonunun doğrulandığı anlamına gelmez.
 */
@Import(AuthenticatedApiTest.Fakes.class)
class SecurityBoundaryTest extends AuthenticatedApiTest {
    static final String PROBE = "/api/v1/game/admin/games/" + UUID.randomUUID();
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^}]+}");

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mappings;

    @Test void everyRegisteredProtectedMethodHasAnonymousAndPlayerBoundaryChecks() throws Exception {
        int checked = 0;
        for (var mapping : mappings.getHandlerMethods().keySet()) {
            for (String template : mapping.getPatternValues()) {
                if (!template.startsWith("/api/v1/game/admin/") && !template.startsWith("/api/v1/game/developer/")
                        && !template.startsWith("/api/v1/game/moderation/") && !template.startsWith("/api/v1/game/feedback")) continue;
                String path = PATH_VARIABLE.matcher(template).replaceAll(UUID.randomUUID().toString());
                for (var method : mapping.getMethodsCondition().getMethods()) {
                    mvc.perform(MockMvcRequestBuilders.request(HttpMethod.valueOf(method.name()), path)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                            .andExpect(status().isUnauthorized())
                            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                            .andExpect(jsonPath("$.status").value(401));
                    if (!template.startsWith("/api/v1/game/feedback"))
                        send(MockMvcRequestBuilders.request(HttpMethod.valueOf(method.name()), path), token(ADMIN_ID, "PLAYER"), "{}")
                                .andExpect(status().isForbidden())
                                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                                .andExpect(jsonPath("$.status").value(403));
                    checked++;
                }
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(29, checked, "Yeni korumalı route eklenirse matris de gözden geçirilmeli");
    }

    @Test void anonymousRequestsToEveryProtectedRouteAreUnauthorizedWithProblemJson() throws Exception {
        UUID id = UUID.randomUUID();
        var requests = List.of(
                get("/api/v1/game/admin/games/" + id), post("/api/v1/game/admin/games"), patch("/api/v1/game/admin/games/" + id),
                post("/api/v1/game/admin/games/" + id + "/archive"), put("/api/v1/game/admin/popular"),
                get("/api/v1/game/admin/games/" + id + "/developers"), post("/api/v1/game/admin/games/" + id + "/developers"),
                get("/api/v1/game/developer/games"), put("/api/v1/game/developer/games/" + id + "/description"),
                post("/api/v1/game/developer/games/" + id + "/media"), delete("/api/v1/game/developer/games/" + id + "/media/" + id),
                post("/api/v1/game/moderation/games/" + id + "/developer-content/actions"));
        for (var request : requests) {
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(header().string("WWW-Authenticate", "Bearer"));
        }
    }

    @Test void invalidTokensAreRejected() throws Exception {
        List<String> tokens = List.of(
                "garbage", "a.b.c",
                sign(claims(ADMIN_ID, List.of("ADMIN")), OTHER_KEYS.getPrivate()),
                sign(claims(ADMIN_ID, List.of("ADMIN")).issuer("https://evil.example"), KEYS.getPrivate()),
                sign(claims(ADMIN_ID, List.of("ADMIN")).audience("another-service"), KEYS.getPrivate()),
                sign(claims(ADMIN_ID, List.of("ADMIN")).expirationTime(Date.from(Instant.now().minusSeconds(600))), KEYS.getPrivate()),
                sign(claims(ADMIN_ID, List.of("ADMIN")).expirationTime(null), KEYS.getPrivate()),
                sign(claims(ADMIN_ID, List.of("ADMIN")).notBeforeTime(Date.from(Instant.now().plusSeconds(600))), KEYS.getPrivate()),
                sign(claims("not-a-uuid", List.of("ADMIN")), KEYS.getPrivate()),
                sign(claims(null, List.of("ADMIN")), KEYS.getPrivate()),
                new PlainJWT(claims(ADMIN_ID, List.of("ADMIN")).build()).serialize());
        for (String token : tokens) send(get(PROBE), token, null).andExpect(status().isUnauthorized());
    }

    @Test void rolesGateEachRouteGroupBeforeAnyBusinessLogic() throws Exception {
        // Yetkili: iş mantığına ulaşır (oyun yok → 404), yetkisiz: 403.
        send(get(PROBE), admin(), null).andExpect(status().isNotFound());
        send(get(PROBE), moderator(), null).andExpect(status().isForbidden());
        send(get(PROBE), developer(), null).andExpect(status().isForbidden());
        send(get(PROBE), token(ADMIN_ID), null).andExpect(status().isForbidden());

        send(get("/api/v1/game/developer/games"), developer(), null).andExpect(status().isOk());
        send(get("/api/v1/game/developer/games"), admin(), null).andExpect(status().isForbidden());
        send(get("/api/v1/game/developer/games"), moderator(), null).andExpect(status().isForbidden());

        String moderate = "/api/v1/game/moderation/games/" + UUID.randomUUID() + "/developer-content/actions";
        String body = "{\"target\":\"DESCRIPTION\",\"action\":\"HIDE\",\"reason\":\"yeterince uzun gerekçe\"}";
        send(post(moderate), moderator(), body).andExpect(status().isNotFound());
        send(post(moderate), admin(), body).andExpect(status().isNotFound());
        send(post(moderate), developer(), body).andExpect(status().isForbidden());
    }

    @Test void roleClaimVariantsAreResolvedCentrally() throws Exception {
        for (Object roles : List.of(List.of("ADMIN"), List.of("PLAYER", "ADMIN")))
            send(get(PROBE), sign(claims(ADMIN_ID, roles), KEYS.getPrivate()), null).andExpect(status().isNotFound());
        for (Object roles : List.of("ADMIN", "ROLE_ADMIN", "admin", "PLAYER ADMIN", "PLAYER,ADMIN", List.of("player", "ROLE_admin"),
                "SUPERUSER", "ADMINISTRATOR", "", List.of(), 42, List.of(1, 2), List.of("ADMIN", 42)))
            send(get(PROBE), sign(claims(ADMIN_ID, roles), KEYS.getPrivate()), null).andExpect(status().isForbidden());
        send(get(PROBE), sign(claims(ADMIN_ID, null), KEYS.getPrivate()), null).andExpect(status().isForbidden());
    }

    @Test void identityComesOnlyFromTheVerifiedBearerToken() throws Exception {
        mvc.perform(get(PROBE).header("X-User-Id", ADMIN_ID.toString()).header("X-Role", "ADMIN")).andExpect(status().isUnauthorized());
        mvc.perform(get(PROBE).cookie(new jakarta.servlet.http.Cookie("access_token", admin()))).andExpect(status().isUnauthorized());
        mvc.perform(get(PROBE).param("access_token", admin())).andExpect(status().isUnauthorized());
        // Geçerli token + sahte başlıklar: kimlik yine token'dan gelir.
        send(get(PROBE).header("X-Role", "ADMIN"), developer(), null).andExpect(status().isForbidden());
    }

    @Test void bearerRequestsNeedNoCsrfTokenButAnonymousMutationsStillFail() throws Exception {
        send(post("/api/v1/game/admin/games/" + UUID.randomUUID() + "/archive"), admin(), "{\"reason\":\"yeterince uzun gerekçe\"}")
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/game/admin/games/" + UUID.randomUUID() + "/archive").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"yeterince uzun gerekçe\"}")).andExpect(status().isUnauthorized());
        // Eski sözleşme: korumasız yazma yolları CSRF/deny-by-default ile reddedilmeye devam eder.
        mvc.perform(post("/api/v1/game/games")).andExpect(status().isForbidden());
    }

    @Test void publicRoutesStayAnonymousAndOtherMethodsStayDenied() throws Exception {
        mvc.perform(get("/api/v1/game/games")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/game/games/batch").contentType(MediaType.APPLICATION_JSON).content("{\"gameIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/game/games/" + UUID.randomUUID() + "/developer-content")).andExpect(status().isNotFound());
        // Fiziksel silme uç noktası yoktur; yetkili admin için bile yöntem desteklenmez.
        send(delete("/api/v1/game/admin/games/" + UUID.randomUUID()), admin(), null).andExpect(status().isMethodNotAllowed());
        send(MockMvcRequestBuilders.request(HttpMethod.GET, "/api/v1/game/unknown"), admin(), null).andExpect(status().is4xxClientError());
    }

    @Test void unsignedAlgNoneTokenIsNotTrusted() throws Exception {
        String header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes());
        String payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"" + ADMIN_ID + "\",\"iss\":\"" + ISSUER + "\",\"aud\":\"" + AUDIENCE + "\",\"roles\":[\"ADMIN\"],\"exp\":"
                        + (Instant.now().getEpochSecond() + 600) + "}").getBytes());
        send(get(PROBE), header + "." + payload + ".", null).andExpect(status().isUnauthorized());
    }
}
