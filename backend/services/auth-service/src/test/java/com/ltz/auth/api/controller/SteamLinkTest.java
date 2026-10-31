package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.ltz.auth.ExternalProviderTestSupport;

import jakarta.servlet.http.Cookie;

/**
 * Oturumdaki hesaba Steam bağlama ve kaldırma (AUTH-20): yeniden doğrulama, hesaba bağlı tek kullanımlık akış, tekillik,
 * son giriş yöntemi koruması ve User Service'e giden olaylar. Steam yerine yerel sahte sağlayıcı kullanılır; üretim kodu
 * aynı HTTP ve imza/nonce doğrulama yolundan geçer.
 */
class SteamLinkTest extends ExternalProviderTestSupport {

    private static final String STEAM_ID_PREFIX = "https://steamcommunity.com/openid/id/";
    private static final String WRONG_PASSWORD = "yanlis-parola-yanlis-parola";

    @DynamicPropertySource
    static void eventTarget(DynamicPropertyRegistry registry) {
        // Olay satırları yazılsın (aktarıcı testlerde elle tetiklenir).
        registry.add("ltz.auth.events.targets", () -> "user");
    }

    @BeforeEach
    void resetProviders() {
        PROVIDERS.reset();
    }

    // ---- Başlatma: yeniden doğrulama ve hesaba bağlı akış ----

    @Test
    void baslatmaYenidenDogrulamaIsterSteamaHesabaBagliYonlendirir() throws Exception {
        UUID account = newAccount();
        String bearer = bearerFor(account);

        // Kimliksiz ve CSRF belirteçsiz istek: diğer oturumlu uçlarla aynı şekilde çerezli CSRF kontrolünde düşer.
        mockMvc.perform(post(AuthApiPaths.STEAM_LINK_START).contentType(MediaType.APPLICATION_JSON)
                        .content(reauth(VALID_PASSWORD)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(AuthApiPaths.STEAM_UNLINK).contentType(MediaType.APPLICATION_JSON)
                        .content(reauth(VALID_PASSWORD)))
                .andExpect(status().isForbidden());
        linkStart(bearer, reauth(WRONG_PASSWORD)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
        assertThat(linkFlowCount(account)).isZero();

        MvcResult started = linkStart(bearer, reauth(VALID_PASSWORD)).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andReturn();
        URI redirect = URI.create(JsonPath.read(started.getResponse().getContentAsString(), "$.redirectUrl"));
        Cookie stateCookie = started.getResponse().getCookie(OAUTH_STATE_COOKIE);
        Map<String, String> query = query(redirect);

        assertThat(redirect.toString()).startsWith(PROVIDERS.url("/steam/openid"));
        assertThat(query).containsEntry("openid.mode", "checkid_setup").containsEntry("openid.realm", PUBLIC_BASE)
                .containsEntry("openid.return_to",
                        PUBLIC_BASE + AuthApiPaths.STEAM_LINK_CALLBACK + "?state=" + stateCookie.getValue());
        assertThat(stateCookie.isHttpOnly()).isTrue();
        assertThat(linkFlowCount(account)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from oauth_link_flows where state_hash = ?", Integer.class,
                stateCookie.getValue())).as("state düz metin saklanmaz, yalnız özeti").isZero();
    }

    @Test
    void parolasizHesapVeZatenSteamBagliHesapBaglamayaBaslayamaz() throws Exception {
        UUID passwordless = newAccount();
        jdbc.update("update accounts set password_hash = null where id = ?", passwordless);
        linkStart(bearerFor(passwordless), reauth(VALID_PASSWORD)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSWORD_REQUIRED"));

        UUID linked = newAccount();
        insertSteamIdentity(linked, newSteamId());
        linkStart(bearerFor(linked), reauth(VALID_PASSWORD)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXTERNAL_IDENTITY_ALREADY_LINKED"));
        assertThat(linkFlowCount(linked)).isZero();
    }

    @Test
    void totpEtkinseIkinciFaktorZorunludur() throws Exception {
        UUID account = newAccount();
        enableTotp(account);
        String bearer = bearerFor(account);

        linkStart(bearer, reauth(VALID_PASSWORD)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TWO_FACTOR_CODE"));
        assertThat(linkFlowCount(account)).isZero();

        linkStart(bearer, """
                {"password":"%s","code":"%s"}
                """.formatted(VALID_PASSWORD, currentTotpCode(account))).andExpect(status().isOk());
        assertThat(linkFlowCount(account)).isEqualTo(1);
    }

    // ---- Tamamlama ----

    @Test
    void basariliBaglamaKimligiYazarOlayiKuyrugaKoyarVeSteamGirisiAcilir() throws Exception {
        UUID account = newAccount();
        String steamId = newSteamId();

        MvcResult result = linkSteam(account, steamId);

        assertThat(fragment(result)).isEqualTo("status=steam_linked");
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).anySatisfy(
                cookie -> assertThat(cookie).contains(OAUTH_STATE_COOKIE + "=").contains("Max-Age=0"));
        assertThat(jdbc.queryForObject("select provider_subject from external_identities where account_id = ? "
                + "and provider = 'STEAM'", String.class, account)).isEqualTo(steamId);
        assertThat(jdbc.queryForObject("select count(*) from oauth_link_flows where account_id = ? "
                + "and consumed_at is not null", Integer.class, account)).isEqualTo(1);
        assertThat(securityEventCount(account, "EXTERNAL_IDENTITY_LINKED")).isEqualTo(1);
        assertThat(outbox(account)).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("event_type", "EXTERNAL_IDENTITY_LINKED")
                    .containsEntry("payload_value", "STEAM:" + steamId).containsEntry("target", "user");
        });

        // Bağlanan Steam kimliği artık bu hesaba giriş sağlar (mevcut giriş akışı, değişmeden).
        Started login = start(AuthApiPaths.STEAM_START);
        MvcResult loggedIn = mockMvc.perform(callbackRequest(AuthApiPaths.STEAM_CALLBACK, login.state(), steamId)
                .cookie(login.stateCookie())).andExpect(status().isFound()).andReturn();
        assertThat(fragment(loggedIn)).isEqualTo("status=authenticated");
    }

    @Test
    void ayniStateIkinciKezVeCerezsizVeBaskaCerezleKullanilamaz() throws Exception {
        UUID account = newAccount();
        LinkStart started = startLink(account);
        String steamId = newSteamId();

        // Çerezsiz ve başka tarayıcının çerezi: bağlama olmaz, akış tüketilmez (sahibi hâlâ tamamlayabilir).
        assertThat(fragment(callback(started.state(), steamId, null))).isEqualTo("error=EXTERNAL_AUTH_FAILED");
        assertThat(fragment(callback(started.state(), steamId, new Cookie(OAUTH_STATE_COOKIE, "baska-tarayici"))))
                .isEqualTo("error=EXTERNAL_AUTH_FAILED");
        assertThat(identityCount(account)).isZero();

        assertThat(fragment(callback(started.state(), steamId, started.stateCookie()))).isEqualTo("status=steam_linked");
        assertThat(fragment(callback(started.state(), newSteamId(), started.stateCookie())))
                .as("tek kullanımlık").isEqualTo("error=EXTERNAL_AUTH_FAILED");
        assertThat(identityCount(account)).isEqualTo(1);
    }

    @Test
    void girisVeBaglamaStateleriBirbirininYerineGecmez() throws Exception {
        UUID account = newAccount();
        String steamId = newSteamId();

        Started login = start(AuthApiPaths.STEAM_START);
        assertThat(fragment(callback(login.state(), steamId, login.stateCookie())))
                .as("giriş state'i bağlama ucunda geçmez").isEqualTo("error=EXTERNAL_AUTH_FAILED");

        LinkStart link = startLink(account);
        MvcResult loginAttempt = mockMvc.perform(callbackRequest(AuthApiPaths.STEAM_CALLBACK, link.state(), steamId)
                .cookie(link.stateCookie())).andExpect(status().isFound()).andReturn();
        assertThat(fragment(loginAttempt)).as("bağlama state'i giriş ucunda geçmez").isEqualTo("error=EXTERNAL_AUTH_FAILED");
        assertThat(identityCount(account)).isZero();
    }

    @Test
    void baskaHesaptaBagliSteamKimligiReddedilirMevcutBagKorunur() throws Exception {
        UUID owner = newAccount();
        String steamId = newSteamId();
        linkSteam(owner, steamId);
        UUID attacker = newAccount();

        MvcResult result = linkSteam(attacker, steamId);

        assertThat(fragment(result)).isEqualTo("error=EXTERNAL_IDENTITY_TAKEN");
        assertThat(identityCount(attacker)).isZero();
        assertThat(jdbc.queryForObject("select account_id from external_identities where provider_subject = ?",
                UUID.class, steamId)).isEqualTo(owner);
        assertThat(securityEventCount(attacker, "EXTERNAL_LINK_CONFLICT")).isEqualTo(1);
        assertThat(outbox(attacker)).isEmpty();
    }

    @Test
    void steamImzasiGecersizseBaglanmaz() throws Exception {
        UUID account = newAccount();
        LinkStart started = startLink(account);
        PROVIDERS.steamValid = false;

        assertThat(fragment(callback(started.state(), newSteamId(), started.stateCookie())))
                .isEqualTo("error=EXTERNAL_AUTH_FAILED");
        assertThat(identityCount(account)).isZero();
        assertThat(outbox(account)).isEmpty();
    }

    // ---- Kaldırma ----

    @Test
    void kaldirmaYenidenDogrulamaIsterBaglantiyiSilerOlayKuyrugaGirer() throws Exception {
        UUID account = newAccount();
        linkSteam(account, newSteamId());
        String bearer = bearerFor(account);

        unlink(bearer, reauth(WRONG_PASSWORD)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
        assertThat(identityCount(account)).isEqualTo(1);

        unlink(bearer, reauth(VALID_PASSWORD)).andExpect(status().isNoContent());
        assertThat(identityCount(account)).isZero();
        assertThat(securityEventCount(account, "EXTERNAL_IDENTITY_UNLINKED")).isEqualTo(1);
        assertThat(outbox(account)).extracting(row -> row.get("event_type"))
                .containsExactlyInAnyOrder("EXTERNAL_IDENTITY_LINKED", "EXTERNAL_IDENTITY_UNLINKED");
        assertThat(payloadValue(account, "EXTERNAL_IDENTITY_UNLINKED")).as("kimlik taşınmaz, yalnız sağlayıcı").isEqualTo("STEAM");

        unlink(bearer, reauth(VALID_PASSWORD)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXTERNAL_IDENTITY_NOT_LINKED"));
    }

    @Test
    void steamTekGirisYontemiyseKaldirilamazBaskaYontemVarsaParolaGerekir() throws Exception {
        UUID steamOnly = newAccount();
        jdbc.update("update accounts set password_hash = null where id = ?", steamOnly);
        insertSteamIdentity(steamOnly, newSteamId());
        unlink(bearerFor(steamOnly), reauth(VALID_PASSWORD)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_LOGIN_METHOD"));
        assertThat(identityCount(steamOnly)).isEqualTo(1);

        // Steam + Google, parola yok: Steam tek yöntem değil ama yeniden doğrulama için parola gerekir.
        UUID steamAndGoogle = newAccount();
        jdbc.update("update accounts set password_hash = null where id = ?", steamAndGoogle);
        insertSteamIdentity(steamAndGoogle, newSteamId());
        jdbc.update("insert into external_identities (id, account_id, provider, provider_subject, linked_at) "
                + "values (gen_random_uuid(), ?, 'GOOGLE', ?, now())", steamAndGoogle, "google-" + uniqueSuffix());
        unlink(bearerFor(steamAndGoogle), reauth(VALID_PASSWORD)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSWORD_REQUIRED"));
        assertThat(identityCount(steamAndGoogle)).isEqualTo(2);
    }

    // ---- Yardımcılar ----

    private UUID newAccount() throws Exception {
        return accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
    }

    private static String reauth(String password) {
        return "{\"password\":\"%s\"}".formatted(password);
    }

    private ResultActions linkStart(String bearer, String body) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.STEAM_LINK_START).header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions unlink(String bearer, String body) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.STEAM_UNLINK).header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Parolayla yeniden doğrulayıp bağlama akışını başlatır; state ve çerez döner. */
    private LinkStart startLink(UUID account) throws Exception {
        MvcResult result = linkStart(bearerFor(account), reauth(VALID_PASSWORD)).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie(OAUTH_STATE_COOKIE);
        return new LinkStart(cookie.getValue(), cookie);
    }

    private record LinkStart(String state, Cookie stateCookie) {
    }

    private MvcResult linkSteam(UUID account, String steamId) throws Exception {
        LinkStart started = startLink(account);
        return callback(started.state(), steamId, started.stateCookie());
    }

    private MvcResult callback(String state, String steamId, Cookie stateCookie) throws Exception {
        MockHttpServletRequestBuilder request = callbackRequest(AuthApiPaths.STEAM_LINK_CALLBACK, state, steamId);
        if (stateCookie != null) {
            request.cookie(stateCookie);
        }
        return mockMvc.perform(request).andExpect(status().isFound()).andReturn();
    }

    private MockHttpServletRequestBuilder callbackRequest(String path, String state, String steamId) {
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("state", new String[]{state});
        params.put("openid.ns", new String[]{"http://specs.openid.net/auth/2.0"});
        params.put("openid.mode", new String[]{"id_res"});
        params.put("openid.op_endpoint", new String[]{PROVIDERS.url("/steam/openid")});
        params.put("openid.claimed_id", new String[]{STEAM_ID_PREFIX + steamId});
        params.put("openid.identity", new String[]{STEAM_ID_PREFIX + steamId});
        params.put("openid.return_to", new String[]{PUBLIC_BASE + path + "?state=" + state});
        params.put("openid.response_nonce", new String[]{Instant.now().toString().substring(0, 19) + "Z" + uniqueSuffix()});
        params.put("openid.assoc_handle", new String[]{"1234567890"});
        params.put("openid.signed", new String[]{
                "signed,op_endpoint,claimed_id,identity,return_to,response_nonce,assoc_handle"});
        params.put("openid.sig", new String[]{"c2lnbmF0dXJl"});
        MockHttpServletRequestBuilder request = get(path);
        params.forEach(request::param);
        return request;
    }

    private void insertSteamIdentity(UUID account, String steamId) {
        jdbc.update("insert into external_identities (id, account_id, provider, provider_subject, linked_at) "
                + "values (gen_random_uuid(), ?, 'STEAM', ?, now())", account, steamId);
    }

    private int linkFlowCount(UUID account) {
        return jdbc.queryForObject("select count(*) from oauth_link_flows where account_id = ?", Integer.class, account);
    }

    private int identityCount(UUID account) {
        return jdbc.queryForObject("select count(*) from external_identities where account_id = ?", Integer.class, account);
    }

    private List<Map<String, Object>> outbox(UUID account) {
        return jdbc.queryForList("select event_type, payload_value, target from auth_event_outbox where account_id = ? "
                + "order by created_at, event_type", account);
    }

    private String payloadValue(UUID account, String eventType) {
        return jdbc.queryForObject("select payload_value from auth_event_outbox where account_id = ? and event_type = ?",
                String.class, account, eventType);
    }

    private static String newSteamId() {
        return "7656119" + String.format("%010d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 10_000_000_000L);
    }
}
