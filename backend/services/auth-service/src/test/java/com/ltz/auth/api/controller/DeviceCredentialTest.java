package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.application.service.AccountAdministrationService;
import com.ltz.auth.domain.enums.AccountStatus;
import com.ltz.auth.infrastructure.security.JwtSigningKey;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import jakarta.servlet.http.Cookie;

/**
 * AUTH-17 masaüstü cihaz kimliği: bağlama (yeniden doğrulama), listeleme, iptal, cihaz token'ı ve hesap güvenliği
 * değişikliklerinde toplu iptal. Sertleştirme: IDOR, tek yanıt, token türü ayrımı, sınırlar, gizli anahtarın yalnız bir kez
 * görünmesi.
 */
class DeviceCredentialTest extends IntegrationTestSupport {

    private static final String LABEL = "Masaüstü PC";

    @Autowired
    private JwtSigningKey signingKey;

    @Autowired
    private AccountAdministrationService administration;

    // ---- Bağlama ve listeleme ----

    @Test
    void baglamaGizliAnahtariBirKezVerirSaklananYalnizOzettir() throws Exception {
        UUID account = newAccount();

        String body = bind(bearerFor(account), LABEL, VALID_PASSWORD).andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.label").value(LABEL))
                .andReturn().getResponse().getContentAsString();

        String deviceId = JsonPath.read(body, "$.deviceId");
        String secret = JsonPath.read(body, "$.deviceSecret");
        assertThat(secret).hasSizeGreaterThanOrEqualTo(43);
        String stored = jdbc.queryForObject("select secret_hash from device_credentials where id = ?::uuid", String.class,
                deviceId);
        assertThat(stored).matches("^[0-9a-f]{64}$").isNotEqualTo(secret).doesNotContain(secret);
        assertThat(securityEventCount(account, "DEVICE_BOUND")).isEqualTo(1);
    }

    @Test
    void listeAnahtariVeOzetiAslaDonmezSadeceKendiCihazlari() throws Exception {
        UUID owner = newAccount();
        UUID other = newAccount();
        String ownerBearer = bearerFor(owner);
        String deviceId = JsonPath.read(bind(ownerBearer, LABEL, VALID_PASSWORD).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.deviceId");
        bind(bearerFor(other), "Başka cihaz", VALID_PASSWORD).andExpect(status().isCreated());

        String list = mockMvc.perform(get(AuthApiPaths.DEVICES).header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].deviceId").value(deviceId))
                .andReturn().getResponse().getContentAsString();

        assertThat(list).doesNotContain("deviceSecret").doesNotContain("secret").doesNotContain("hash");
    }

    @Test
    void yanlisParolaBaglamayiReddeder() throws Exception {
        UUID account = newAccount();

        bind(bearerFor(account), LABEL, "yanlis-parola-yanlis-parola").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));

        assertThat(deviceCount(account)).isZero();
        assertThat(securityEventCount(account, "DEVICE_BIND_REJECTED")).isEqualTo(1);
    }

    @Test
    void totpEtkinseIkinciFaktorZorunludur() throws Exception {
        UUID account = newAccount();
        enableTotp(account);
        String bearer = bearerFor(account);

        bind(bearer, LABEL, VALID_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TWO_FACTOR_CODE"));
        assertThat(deviceCount(account)).isZero();

        mockMvc.perform(post(AuthApiPaths.DEVICES).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"%s","password":"%s","code":"%s"}
                                """.formatted(LABEL, VALID_PASSWORD, currentTotpCode(account))))
                .andExpect(status().isCreated());
    }

    @Test
    void cihazAdiIsaretlemeKarakteriVeAsiriUzunlukKabulEtmez() throws Exception {
        String bearer = bearerFor(newAccount());

        for (String label : new String[] {"<script>x</script>", "a\"b", "", "   ", "x".repeat(65), "a&b"}) {
            bind(bearer, label, VALID_PASSWORD).andExpect(status().isBadRequest());
        }
    }

    @Test
    void aktifCihazSiniriAsilamazIptalSonrasiYenisiBaglanir() throws Exception {
        UUID account = newAccount();
        String bearer = bearerFor(account);
        String first = null;
        for (int index = 0; index < 5; index++) {
            String body = bind(bearer, "Cihaz " + index, VALID_PASSWORD).andExpect(status().isCreated()).andReturn()
                    .getResponse().getContentAsString();
            first = first == null ? JsonPath.read(body, "$.deviceId") : first;
        }

        bind(bearer, "Altıncı", VALID_PASSWORD).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEVICE_LIMIT_REACHED"));

        mockMvc.perform(delete(AuthApiPaths.DEVICES + "/" + first).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());
        bind(bearer, "Altıncı", VALID_PASSWORD).andExpect(status().isCreated());
    }

    @Test
    void gunlukBaglamaSiniriIptalBaglamaDongusuyleAsilamaz() throws Exception {
        UUID account = newAccount();
        String bearer = bearerFor(account);
        for (int index = 0; index < 10; index++) {
            String id = JsonPath.read(bind(bearer, "Döngü " + index, VALID_PASSWORD).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString(), "$.deviceId");
            mockMvc.perform(delete(AuthApiPaths.DEVICES + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isNoContent());
        }

        bind(bearer, "Fazla", VALID_PASSWORD).andExpect(status().isTooManyRequests());
    }

    // ---- İptal ----

    @Test
    void iptalYalnizKendiCihaziIcinIdempotentBaskasininkiAyirtEdilmeden404() throws Exception {
        UUID owner = newAccount();
        UUID stranger = newAccount();
        String ownerBearer = bearerFor(owner);
        Bound device = bound(ownerBearer);
        String deviceId = device.deviceId();

        mockMvc.perform(delete(AuthApiPaths.DEVICES + "/" + deviceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(stranger)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DEVICE_NOT_FOUND"));
        mockMvc.perform(delete(AuthApiPaths.DEVICES + "/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isNotFound());
        assertThat(deviceStatus(deviceId)).isEqualTo("ACTIVE");

        mockMvc.perform(delete(AuthApiPaths.DEVICES + "/" + deviceId).header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(AuthApiPaths.DEVICES + "/" + deviceId).header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isNoContent());

        assertThat(deviceStatus(deviceId)).isEqualTo("REVOKED");
        assertThat(securityEventCount(owner, "DEVICE_REVOKED")).isEqualTo(1);
        tokenRequest(deviceId, device.secret()).andExpect(status().isUnauthorized());
    }

    @Test
    void kimliksizIstekVeCsrfsizCerezliIstekReddedilir() throws Exception {
        UUID account = newAccount();
        String email = jdbc.queryForObject("select email from accounts where id = ?", String.class, account);
        mockMvc.perform(get(AuthApiPaths.DEVICES)).andExpect(status().isUnauthorized());
        // Durum değiştiren uçlarda CSRF denetimi kimlik denetiminden önce çalışır (diğer oturumlu uçlarla aynı).
        mockMvc.perform(post(AuthApiPaths.DEVICES).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"x\",\"password\":\"y\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(AuthApiPaths.DEVICES).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"x\",\"password\":\"y\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(AuthApiPaths.DEVICES + "/" + UUID.randomUUID()).with(xsrf()))
                .andExpect(status().isUnauthorized());

        SessionCookiePair session = loginSession(email);
        Cookie access = session.access();
        String json = "{\"label\":\"" + LABEL + "\",\"password\":\"" + VALID_PASSWORD + "\"}";
        mockMvc.perform(post(AuthApiPaths.DEVICES).cookie(access).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(AuthApiPaths.DEVICES).cookie(access).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }

    // ---- Cihaz token'ı ----

    @Test
    void cihazTokeniSozlesmeyeUygunDarYetkiliKisaOmurluVeImzalidir() throws Exception {
        UUID account = newAccount();
        Bound device = bound(bearerFor(account));

        String body = tokenRequest(device.deviceId(), device.secret()).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.expiresIn").value(60))
                .andExpect(jsonPath("$.scope").value("hardware:sync:write"))
                .andReturn().getResponse().getContentAsString();

        SignedJWT jwt = SignedJWT.parse(JsonPath.read(body, "$.accessToken"));
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) TEST_JWT_KEYS.getPublic()))).isTrue();
        assertThat(jwt.getHeader().getType().getType()).isEqualTo("dev+jwt");
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(signingKey.keyId());
        assertThat(claims.getIssuer()).isEqualTo("ltz-auth");
        assertThat(claims.getAudience()).containsExactly("hardware-service");
        assertThat(claims.getSubject()).isEqualTo(device.deviceId());
        assertThat(claims.getStringClaim("account_id")).isEqualTo(account.toString());
        assertThat(claims.getStringClaim("scope")).isEqualTo("hardware:sync:write");
        assertThat(claims.getJWTID()).isNotBlank();
        assertThat(claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()).isEqualTo(60_000L);
        assertThat(claims.getClaims()).doesNotContainKeys("roles", "sid", "ver", "amr");
    }

    @Test
    void yanlisBilinmeyenIptalSuresiDolmusCihazVeAktifOlmayanHesapAyniYaniti() throws Exception {
        UUID account = newAccount();
        Bound device = bound(bearerFor(account));
        String wrong = tokenRequest(device.deviceId(), "x".repeat(43)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_DEVICE_CREDENTIALS")).andReturn().getResponse()
                .getContentAsString();

        String unknown = tokenRequest(UUID.randomUUID().toString(), device.secret()).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(unknown).isEqualTo(wrong);

        Bound expired = bound(bearerFor(account));
        jdbc.update("update device_credentials set created_at = now() - interval '3 days', "
                + "expires_at = now() - interval '1 day' where id = ?::uuid", expired.deviceId());
        assertThat(tokenRequest(expired.deviceId(), expired.secret()).andExpect(status().isUnauthorized()).andReturn()
                .getResponse().getContentAsString()).isEqualTo(wrong);

        UUID actor = newAccount();
        administration.changeStatus(actor, account, AccountStatus.SUSPENDED, "test");
        assertThat(tokenRequest(device.deviceId(), device.secret()).andExpect(status().isUnauthorized()).andReturn()
                .getResponse().getContentAsString()).isEqualTo(wrong);
    }

    @Test
    void tokenUcuCerezOkumazVeCsrfIstemez() throws Exception {
        UUID account = newAccount();
        Bound device = bound(bearerFor(account));
        String email = jdbc.queryForObject("select email from accounts where id = ?", String.class, account);

        mockMvc.perform(post(AuthApiPaths.DEVICES_TOKEN).cookie(loginSession(email).access())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"%s\",\"deviceSecret\":\"%s\"}".formatted(device.deviceId(), device.secret())))
                .andExpect(status().isOk());
        postJson(AuthApiPaths.DEVICES_TOKEN, "{\"deviceId\":\"not-a-uuid\",\"deviceSecret\":\"x\"}")
                .andExpect(status().isBadRequest());
        postJson(AuthApiPaths.DEVICES_TOKEN, "{\"deviceId\":\"%s\"}".formatted(device.deviceId()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cihazTokeniAuthinHicbirUcundaGecmez() throws Exception {
        UUID account = newAccount();
        Bound device = bound(bearerFor(account));
        String body = tokenRequest(device.deviceId(), device.secret()).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        String bearer = "Bearer " + JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get(AuthApiPaths.DEVICES).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/v1/accounts/" + account).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(AuthApiPaths.SESSION_LOGOUT_ALL).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(AuthApiPaths.ADMIN_ROLES).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized());
    }

    // ---- Hesap güvenliği değişince cihazlar kapanır ----

    @Test
    void parolaDegisinceTumCihazlarIptalOlurTokenAlinamaz() throws Exception {
        UUID account = newAccount();
        String bearer = bearerFor(account);
        Bound device = bound(bearer);
        tokenRequest(device.deviceId(), device.secret()).andExpect(status().isOk());

        mockMvc.perform(post(AuthApiPaths.PASSWORD_CHANGE).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"yeni-parola-yeni-parola-1","newPasswordConfirmation":"yeni-parola-yeni-parola-1"}
                                """.formatted(VALID_PASSWORD)))
                .andExpect(status().is2xxSuccessful());

        assertThat(deviceStatus(device.deviceId())).isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject("select revoke_reason from device_credentials where id = ?::uuid", String.class,
                device.deviceId())).isEqualTo("PASSWORD_CHANGED");
        tokenRequest(device.deviceId(), device.secret()).andExpect(status().isUnauthorized());
    }

    @Test
    void hesapAskiyaAlinincaCihazlarIptalOlur() throws Exception {
        UUID account = newAccount();
        Bound device = bound(bearerFor(account));

        administration.changeStatus(newAccount(), account, AccountStatus.BANNED, "test");

        assertThat(deviceStatus(device.deviceId())).isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject("select revoke_reason from device_credentials where id = ?::uuid", String.class,
                device.deviceId())).isEqualTo("ACCOUNT_INACTIVE");
    }

    // ---- Yardımcılar ----

    private UUID newAccount() throws Exception {
        return accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
    }

    private ResultActions bind(String bearer, String label, String password) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.DEVICES).header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"label":"%s","password":"%s"}
                        """.formatted(label, password)));
    }

    private Bound bound(String bearer) throws Exception {
        String body = bind(bearer, LABEL, VALID_PASSWORD).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return new Bound(JsonPath.read(body, "$.deviceId"), JsonPath.read(body, "$.deviceSecret"));
    }

    private ResultActions tokenRequest(String deviceId, String secret) throws Exception {
        return postJson(AuthApiPaths.DEVICES_TOKEN,
                "{\"deviceId\":\"%s\",\"deviceSecret\":\"%s\"}".formatted(deviceId, secret));
    }

    private int deviceCount(UUID account) {
        return jdbc.queryForObject("select count(*) from device_credentials where account_id = ?", Integer.class, account);
    }

    private String deviceStatus(String deviceId) {
        return jdbc.queryForObject("select status from device_credentials where id = ?::uuid", String.class, deviceId);
    }

    private record Bound(String deviceId, String secret) {
    }
}
