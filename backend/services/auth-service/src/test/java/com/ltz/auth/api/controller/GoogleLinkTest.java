package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ltz.auth.ExternalProviderTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;
import com.ltz.auth.application.port.SecurityNotificationMail;
import com.ltz.auth.application.port.SecurityNotificationType;

import jakarta.servlet.http.Cookie;

/**
 * Oturumdaki hesaba Google bağlama (AUTH-20 devamı): yeniden doğrulama, hesaba bağlı tek kullanımlık akış, PKCE/nonce,
 * tekillik, kalıcılık (kullanıcı tarafı kaldırma yok) ve User Service'e giden olay. Google yerine yerel sahte sağlayıcı
 * kullanılır; üretim kodu aynı token uç noktası ve imza/nonce doğrulama yolundan geçer.
 */
@Import(RecordingMailPortConfiguration.class)
class GoogleLinkTest extends ExternalProviderTestSupport {

    @Autowired
    private RecordingMailPortConfiguration.RecordingMailPort mailPort;

    private static final String WRONG_PASSWORD = "yanlis-parola-yanlis-parola";

    @DynamicPropertySource
    static void eventTarget(DynamicPropertyRegistry registry) {
        registry.add("ltz.auth.events.targets", () -> "user");
    }

    @BeforeEach
    void resetProviders() {
        PROVIDERS.reset();
    }

    // ---- Başlatma ----

    @Test
    void baslatmaYenidenDogrulamaIsterGoogleaPkceVeNonceIleYonlendirir() throws Exception {
        UUID account = newAccount();
        String bearer = bearerFor(account);

        mockMvc.perform(post(AuthApiPaths.GOOGLE_LINK_START).contentType(MediaType.APPLICATION_JSON)
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

        assertThat(query).containsEntry("response_type", "code").containsEntry("client_id", GOOGLE_CLIENT_ID)
                .containsEntry("redirect_uri", PUBLIC_BASE + AuthApiPaths.GOOGLE_LINK_CALLBACK)
                .containsEntry("code_challenge_method", "S256").containsEntry("state", stateCookie.getValue())
                .containsKeys("nonce", "code_challenge");
        assertThat(stateCookie.isHttpOnly()).isTrue();
        assertThat(linkFlowCount(account)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from oauth_link_flows where state_hash = ?", Integer.class,
                stateCookie.getValue())).as("state düz metin saklanmaz, yalnız özeti").isZero();
        assertThat(jdbc.queryForObject("select provider from oauth_link_flows where account_id = ?", String.class,
                account)).isEqualTo("GOOGLE");
    }

    @Test
    void parolasizHesapVeZatenGoogleBagliHesapBaglamayaBaslayamaz() throws Exception {
        UUID passwordless = newAccount();
        jdbc.update("update accounts set password_hash = null where id = ?", passwordless);
        linkStart(bearerFor(passwordless), reauth(VALID_PASSWORD)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSWORD_REQUIRED"));

        UUID linked = newAccount();
        insertGoogleIdentity(linked, "g-" + uniqueSuffix());
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
    void basariliBaglamaKimligiYazarOlayiKuyrugaKoyarVeGoogleGirisiAyniHesabaAcilir() throws Exception {
        UUID account = newAccount();
        String subject = "g-" + uniqueSuffix();

        // Google hesabının e-postası Auth hesabınınkinden farklı: eşleştirme yalnız sub ile yapılır.
        MvcResult result = linkGoogle(account, subject, uniqueEmail());

        assertThat(fragment(result)).isEqualTo("status=google_linked");
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).anySatisfy(
                cookie -> assertThat(cookie).contains(OAUTH_STATE_COOKIE + "=").contains("Max-Age=0"));
        assertThat(jdbc.queryForObject("select provider_subject from external_identities where account_id = ? "
                + "and provider = 'GOOGLE'", String.class, account)).isEqualTo(subject);
        assertThat(jdbc.queryForObject("select count(*) from oauth_link_flows where account_id = ? "
                + "and consumed_at is not null", Integer.class, account)).isEqualTo(1);
        assertThat(securityEventCount(account, "EXTERNAL_IDENTITY_LINKED")).isEqualTo(1);
        String ownerEmail = jdbc.queryForObject("select email from accounts where id = ?", String.class, account);
        mailPort.awaitNotification(ownerEmail, SecurityNotificationType.EXTERNAL_IDENTITY_LINKED);
        assertThat(mailPort.notificationsTo(ownerEmail)).extracting(SecurityNotificationMail::type)
                .containsExactly(SecurityNotificationType.EXTERNAL_IDENTITY_LINKED);
        assertThat(outbox(account)).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("event_type", "EXTERNAL_IDENTITY_LINKED")
                .containsEntry("payload_value", "GOOGLE:" + subject).containsEntry("target", "user"));

        // Bağlanan Google kimliği artık bu hesaba giriş sağlar ve yeni hesap açmaz.
        int accountsBefore = jdbc.queryForObject("select count(*) from accounts", Integer.class);
        Started login = start(AuthApiPaths.GOOGLE_START);
        String code = "code-" + uniqueSuffix();
        PROVIDERS.idTokensByCode.put(code,
                googleIdToken(subject, uniqueEmail(), true, query(login.location()).get("nonce")));
        MvcResult loggedIn = mockMvc.perform(get(AuthApiPaths.GOOGLE_CALLBACK).param("code", code)
                .param("state", login.state()).cookie(login.stateCookie())).andExpect(status().isFound()).andReturn();
        assertThat(fragment(loggedIn)).isEqualTo("status=authenticated");
        assertThat(jdbc.queryForObject("select count(*) from accounts", Integer.class)).isEqualTo(accountsBefore);
    }

    @Test
    void ayniStateIkinciKezVeCerezsizVeBaskaCerezleKullanilamaz() throws Exception {
        UUID account = newAccount();
        LinkStart started = startLink(account);
        String subject = "g-" + uniqueSuffix();

        // Çerezsiz ve farklı çerezli dönüş reddedilir; akış tüketilmez.
        assertThat(fragment(callback(started, subject, uniqueEmail(), null))).isEqualTo("error=EXTERNAL_AUTH_FAILED");
        assertThat(fragment(callback(started, subject, uniqueEmail(), new Cookie(OAUTH_STATE_COOKIE, "baska"))))
                .isEqualTo("error=EXTERNAL_AUTH_FAILED");
        assertThat(identityCount(account)).isZero();

        assertThat(fragment(callback(started, subject, uniqueEmail(), started.stateCookie())))
                .isEqualTo("status=google_linked");
        // Tek kullanımlık: aynı state ikinci kez çalışmaz.
        assertThat(fragment(callback(started, subject, uniqueEmail(), started.stateCookie())))
                .isEqualTo("error=EXTERNAL_AUTH_FAILED");
        assertThat(identityCount(account)).isEqualTo(1);
    }

    @Test
    void girisVeBaglamaStateleriBirbirininYerineGecmez() throws Exception {
        UUID account = newAccount();
        Started login = start(AuthApiPaths.GOOGLE_START);
        String code = "code-" + uniqueSuffix();
        PROVIDERS.idTokensByCode.put(code,
                googleIdToken("g-" + uniqueSuffix(), uniqueEmail(), true, query(login.location()).get("nonce")));

        // Giriş state'i bağlama callback'inde geçersizdir.
        MvcResult result = mockMvc.perform(get(AuthApiPaths.GOOGLE_LINK_CALLBACK).param("code", code)
                .param("state", login.state()).cookie(login.stateCookie())).andExpect(status().isFound()).andReturn();
        assertThat(fragment(result)).isEqualTo("error=EXTERNAL_AUTH_FAILED");
        assertThat(identityCount(account)).isZero();

        // Bağlama state'i giriş callback'inde geçersizdir.
        LinkStart link = startLink(account);
        MvcResult asLogin = mockMvc.perform(get(AuthApiPaths.GOOGLE_CALLBACK).param("code", code)
                .param("state", link.state()).cookie(link.stateCookie())).andExpect(status().isFound()).andReturn();
        assertThat(fragment(asLogin)).isEqualTo("error=EXTERNAL_AUTH_FAILED");
        assertThat(identityCount(account)).isZero();
    }

    @Test
    void baskaHesaptaBagliGoogleKimligiReddedilirMevcutBagKorunur() throws Exception {
        String subject = "g-" + uniqueSuffix();
        UUID owner = newAccount();
        insertGoogleIdentity(owner, subject);
        UUID other = newAccount();

        MvcResult result = linkGoogle(other, subject, uniqueEmail());

        assertThat(fragment(result)).isEqualTo("error=EXTERNAL_IDENTITY_TAKEN");
        assertThat(identityCount(other)).isZero();
        assertThat(identityCount(owner)).isEqualTo(1);
        assertThat(securityEventCount(other, "EXTERNAL_LINK_CONFLICT")).isEqualTo(1);
    }

    @Test
    void googleKodDegisimiBasarisizsaBaglanmaz() throws Exception {
        UUID account = newAccount();
        LinkStart started = startLink(account);
        PROVIDERS.tokenEndpointFails = true;

        MvcResult result = callback(started, "g-" + uniqueSuffix(), uniqueEmail(), started.stateCookie());

        assertThat(fragment(result)).isEqualTo("error=EXTERNAL_AUTH_FAILED");
        assertThat(identityCount(account)).isZero();
    }

    @Test
    void yanlisNonceVeKodsuzDonusBaglamaz() throws Exception {
        UUID account = newAccount();
        LinkStart started = startLink(account);
        String code = "code-" + uniqueSuffix();
        PROVIDERS.idTokensByCode.put(code, googleIdToken("g-" + uniqueSuffix(), uniqueEmail(), true, "yanlis-nonce"));

        MvcResult wrongNonce = mockMvc.perform(get(AuthApiPaths.GOOGLE_LINK_CALLBACK).param("code", code)
                .param("state", started.state()).cookie(started.stateCookie())).andExpect(status().isFound()).andReturn();
        assertThat(fragment(wrongNonce)).isEqualTo("error=EXTERNAL_AUTH_FAILED");

        LinkStart second = startLink(account);
        MvcResult noCode = mockMvc.perform(get(AuthApiPaths.GOOGLE_LINK_CALLBACK).param("state", second.state())
                .cookie(second.stateCookie())).andExpect(status().isFound()).andReturn();
        assertThat(fragment(noCode)).isEqualTo("error=EXTERNAL_AUTH_FAILED");
        assertThat(identityCount(account)).isZero();
    }

    @Test
    void kullaniciTarafiKaldirmaUcuYokBagliKimlikKalir() throws Exception {
        UUID account = newAccount();
        linkGoogle(account, "g-" + uniqueSuffix(), uniqueEmail());

        mockMvc.perform(post(AuthApiPaths.OAUTH + "/google/unlink").header(HttpHeaders.AUTHORIZATION, bearerFor(account))
                        .contentType(MediaType.APPLICATION_JSON).content(reauth(VALID_PASSWORD)))
                .andExpect(status().is4xxClientError());
        assertThat(identityCount(account)).isEqualTo(1);
    }

    // ---- Yardımcılar ----

    private UUID newAccount() throws Exception {
        return accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
    }

    private static String reauth(String password) {
        return "{\"password\":\"%s\"}".formatted(password);
    }

    private ResultActions linkStart(String bearer, String body) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.GOOGLE_LINK_START).header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private record LinkStart(String state, String nonce, Cookie stateCookie) {
    }

    /** Parolayla yeniden doğrulayıp bağlama akışını başlatır; state, nonce ve çerez döner. */
    private LinkStart startLink(UUID account) throws Exception {
        MvcResult result = linkStart(bearerFor(account), reauth(VALID_PASSWORD)).andExpect(status().isOk()).andReturn();
        URI redirect = URI.create(JsonPath.read(result.getResponse().getContentAsString(), "$.redirectUrl"));
        Cookie cookie = result.getResponse().getCookie(OAUTH_STATE_COOKIE);
        return new LinkStart(cookie.getValue(), query(redirect).get("nonce"), cookie);
    }

    private MvcResult linkGoogle(UUID account, String subject, String googleEmail) throws Exception {
        LinkStart started = startLink(account);
        return callback(started, subject, googleEmail, started.stateCookie());
    }

    /** Sağlayıcı dönüşü: sahte token uç noktası bu kod için imzalı kimlik belirteci verir. */
    private MvcResult callback(LinkStart started, String subject, String googleEmail, Cookie cookie) throws Exception {
        String code = "code-" + uniqueSuffix();
        PROVIDERS.idTokensByCode.put(code, googleIdToken(subject, googleEmail, true, started.nonce()));
        var request = get(AuthApiPaths.GOOGLE_LINK_CALLBACK).param("code", code).param("state", started.state());
        if (cookie != null) {
            request.cookie(cookie);
        }
        return mockMvc.perform(request).andExpect(status().isFound()).andReturn();
    }

    private void insertGoogleIdentity(UUID account, String subject) {
        jdbc.update("insert into external_identities (id, account_id, provider, provider_subject, linked_at) "
                + "values (gen_random_uuid(), ?, 'GOOGLE', ?, now())", account, subject);
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
}
