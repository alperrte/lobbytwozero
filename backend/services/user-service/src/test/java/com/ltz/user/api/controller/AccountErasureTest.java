package com.ltz.user.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.user.IntegrationTestSupport;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

/**
 * Auth → User hesap verisi silme (iç uç). Yalnız Auth'un imzaladığı, {@code scope=account:erase} taşıyan servis
 * token'ı ({@code svc+jwt}) kabul edilir; kullanıcı token'ı kabul edilmez ve tersi de geçerlidir. Senaryolar
 * docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md §5'teki 17 maddeyi karşılar.
 */
class AccountErasureTest extends IntegrationTestSupport {

    // ------------------------------------------------------------ silme davranışı (1-4, 16)

    @Test
    void servisTokeniyleHesabinTumVerisiSilinir204() throws Exception {
        UUID account = newUser();
        seed(account);

        erase(account, serviceTokenValue(account)).andExpect(status().isNoContent());

        assertThat(rowsOf(account)).as("silinen hesabın hiçbir verisi kalmamalı").isZero();
    }

    @Test
    void silmeIdempotentIkinciCagri204() throws Exception {
        UUID account = newUser();
        seed(account);

        erase(account, serviceTokenValue(account)).andExpect(status().isNoContent());
        erase(account, serviceTokenValue(account)).andExpect(status().isNoContent());

        assertThat(rowsOf(account)).isZero();
    }

    @Test
    void hicVerisiOlmayanHesap204() throws Exception {
        UUID unknown = UUID.randomUUID();

        erase(unknown, serviceTokenValue(unknown)).andExpect(status().isNoContent());
    }

    @Test
    void silmeBaskaHesaplarinVerisineDokunmaz() throws Exception {
        UUID account = newUser();
        UUID other = newUser();
        seed(account);
        seed(other);
        int before = rowsOf(other);

        erase(account, serviceTokenValue(account)).andExpect(status().isNoContent());

        assertThat(rowsOf(account)).isZero();
        assertThat(rowsOf(other)).isEqualTo(before).isPositive();
    }

    @Test
    void gecersizHesapKimligi400() throws Exception {
        mockMvc.perform(delete("/internal/v1/accounts/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTokenValue(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    // ------------------------------------------------------------ kimlik ve yetki (5-9)

    @Test
    void tokensizCagri401VeriSilinmez() throws Exception {
        UUID account = newUser();
        seed(account);
        int before = rowsOf(account);

        mockMvc.perform(delete(InternalApiPaths.ACCOUNT_ERASURE, account))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(rowsOf(account)).isEqualTo(before);
    }

    @Test
    void kullaniciTokeniIcUcuAcamazADMINRoluDahil401() throws Exception {
        UUID account = newUser();
        seed(account);
        int before = rowsOf(account);
        String adminUserToken = accessToken(account, claims -> claims.claim("roles", List.of("ADMIN")));

        erase(account, adminUserToken).andExpect(status().isUnauthorized());
        erase(account, accessToken(account, UnaryOperator.identity())).andExpect(status().isUnauthorized());

        assertThat(rowsOf(account)).isEqualTo(before);
    }

    @Test
    void yanlisAudienceIssuerVeTur401() throws Exception {
        UUID account = newUser();

        erase(account, serviceToken(account, claims -> claims.audience("social-service")))
                .andExpect(status().isUnauthorized());
        erase(account, serviceToken(account, claims -> claims.audience("ltz-api")))
                .andExpect(status().isUnauthorized());
        erase(account, serviceToken(account, claims -> claims.issuer("evil-issuer")))
                .andExpect(status().isUnauthorized());
        erase(account, sign(SIGNING_KEY, JOSEObjectType.JWT, validServiceClaims(account).build()))
                .andExpect(status().isUnauthorized());
        erase(account, sign(SIGNING_KEY, ACCESS_TOKEN_TYPE, validServiceClaims(account).build()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void kapsamEksikVeyaFarkliysa403VeriSilinmez() throws Exception {
        UUID account = newUser();
        seed(account);
        int before = rowsOf(account);

        erase(account, serviceToken(account, claims -> claims.claim("scope", "account:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        erase(account, serviceToken(account, claims -> claims.claim("scope", null)))
                .andExpect(status().isForbidden());
        erase(account, serviceToken(account, claims -> claims.claim("scope", "account:erase-all")))
                .andExpect(status().isForbidden());
        erase(account, serviceToken(account, claims -> claims.claim("scope", "events:ingest")))
                .andExpect(status().isForbidden());

        assertThat(rowsOf(account)).isEqualTo(before);
    }

    @Test
    void baskaServisinTokeniHesapSilemez403VeriSilinmez() throws Exception {
        UUID account = newUser();
        seed(account);
        int before = rowsOf(account);

        // Diğer her şey geçerli (aud, scope, account_id) ama çağıran Auth değil.
        erase(account, serviceToken(account, claims -> claims.subject("svc:admin-service")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        erase(account, serviceToken(account, claims -> claims.subject("svc:auth-service-evil")))
                .andExpect(status().isForbidden());

        assertThat(rowsOf(account)).isEqualTo(before);
    }

    // ------------------------------------------------------------ hesap bağı (10-11)

    @Test
    void tokenBaskaHesabaBagliysa403VeriSilinmez() throws Exception {
        UUID account = newUser();
        seed(account);
        int before = rowsOf(account);

        // account_id başka bir hesap
        erase(account, serviceToken(account, claims -> claims.claim("account_id", UUID.randomUUID().toString())))
                .andExpect(status().isForbidden());
        // account_id yok
        erase(account, serviceToken(account, claims -> claims.claim("account_id", null)))
                .andExpect(status().isForbidden());
        // başka hesap için üretilmiş geçerli token yeniden oynatılıyor
        erase(account, serviceTokenValue(newUser())).andExpect(status().isForbidden());

        assertThat(rowsOf(account)).isEqualTo(before);
    }

    @Test
    void yolVeClaimdeAyniUuidFarkliHarfBuyuklugunde204() throws Exception {
        UUID account = newUser();
        seed(account);

        // Yol UUID olarak çözülür; claim küçük harfli (Auth'un yolladığı biçim) olduğundan eşleşmeli.
        mockMvc.perform(delete("/internal/v1/accounts/" + account.toString().toUpperCase())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTokenValue(account)))
                .andExpect(status().isNoContent());

        assertThat(rowsOf(account)).isZero();
    }

    @Test
    void fazlaKapsamliTokendeDeErasekapsamiYeterli() throws Exception {
        UUID account = newUser();

        erase(account, serviceToken(account, claims -> claims.claim("scope", "account:read account:erase")))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------ token sözleşmesi (12-13)

    @Test
    void servisTokeniSozlesmeKurallariOmurSubJtiSuresi() throws Exception {
        UUID account = newUser();
        Instant now = Instant.now();

        // Ömür 60 sn'yi aşıyor.
        erase(account, serviceToken(account, claims -> claims.expirationTime(Date.from(now.plusSeconds(600)))))
                .andExpect(status().isUnauthorized());
        // Süresi dolmuş.
        erase(account, serviceToken(account, claims -> claims.issueTime(Date.from(now.minusSeconds(600)))
                .notBeforeTime(Date.from(now.minusSeconds(600))).expirationTime(Date.from(now.minusSeconds(540)))))
                .andExpect(status().isUnauthorized());
        // sub svc: ile başlamıyor, jti yok.
        erase(account, serviceToken(account, claims -> claims.subject(UUID.randomUUID().toString())))
                .andExpect(status().isUnauthorized());
        erase(account, serviceToken(account, claims -> claims.jwtID(null))).andExpect(status().isUnauthorized());
        // iat çıkarılarak ömür kuralı aşılamaz: Spring iat'ı exp-1 sayar, bu yüzden bitiş zamanı ayrıca denetlenir.
        erase(account, serviceToken(account,
                claims -> claims.issueTime(null).expirationTime(Date.from(now.plusSeconds(3600)))))
                .andExpect(status().isUnauthorized());
        // iat yok ve süre kısa görünüyor: iat zorunludur.
        erase(account, serviceToken(account, claims -> claims.issueTime(null))).andExpect(status().isUnauthorized());
    }

    @Test
    void imzaSaldirilari401BaskaAnahtarNoneHs256BozukImzaBilinmeyenKid() throws Exception {
        UUID account = newUser();
        seed(account);
        int before = rowsOf(account);
        RSAKey attacker = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        RSAKey unknownKid = new RSAKeyGenerator(2048).keyID("unknown-kid").generate();
        JWTClaimsSet claims = validServiceClaims(account).build();

        erase(account, sign(attacker, SERVICE_TOKEN_TYPE, claims)).andExpect(status().isUnauthorized());
        erase(account, sign(unknownKid, SERVICE_TOKEN_TYPE, claims)).andExpect(status().isUnauthorized());
        erase(account, new PlainJWT(claims).serialize()).andExpect(status().isUnauthorized());
        SignedJWT hmac = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(KEY_ID)
                .type(SERVICE_TOKEN_TYPE).build(), claims);
        hmac.sign(new MACSigner(SIGNING_KEY.toPublicJWK().toRSAPublicKey().getEncoded()));
        erase(account, hmac.serialize()).andExpect(status().isUnauthorized());
        String token = serviceTokenValue(account);
        erase(account, token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA"))
                .andExpect(status().isUnauthorized());

        assertThat(rowsOf(account)).isEqualTo(before);
    }

    // ------------------------------------------------------------ uç ayrımı (14-15)

    @Test
    void servisTokeniKullaniciUclarindaGecmez401() throws Exception {
        String serviceToken = serviceTokenValue(newUser());

        mockMvc.perform(get(UserApiPaths.ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void digerIcYollarVeYontemlerKapaliServisTokeniyleDe() throws Exception {
        UUID account = newUser();
        String bearer = "Bearer " + serviceTokenValue(account);

        mockMvc.perform(get(InternalApiPaths.ACCOUNT_ERASURE, account).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(InternalApiPaths.ACCOUNT_ERASURE, account).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/v1/anything").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ uyumluluk (17)

    @Test
    void authinGercekIsleyicisininUrettigiBicimdeTokenKabulEdilir() throws Exception {
        UUID account = newUser();
        seed(account);
        Instant now = Instant.now();
        // ServiceTokenIssuer: typ=svc+jwt, kid, aud=[hedef], sub=svc:auth-service, scope, account_id, iat/nbf, 30 sn, jti.
        String token = sign(SIGNING_KEY, SERVICE_TOKEN_TYPE, new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(AUTH_SUBJECT)
                .audience(List.of(SERVICE_AUDIENCE))
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(30)))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", ERASE_SCOPE)
                .claim("account_id", account.toString())
                .build());

        erase(account, token).andExpect(status().isNoContent());

        assertThat(rowsOf(account)).isZero();
    }

    // ------------------------------------------------------------ yardımcılar

    private static String serviceTokenValue(UUID account) {
        return serviceToken(account, UnaryOperator.identity());
    }

    private ResultActions erase(UUID account, String token) throws Exception {
        return mockMvc.perform(delete(InternalApiPaths.ACCOUNT_ERASURE, account)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    /** Hesabın her tabloda verisi olacak şekilde veri üretir (profil + profil görselleri). */
    private void seed(UUID account) {
        insertProfile(account);
        insertMedia(account, "AVATAR");
        insertMedia(account, "COVER");
        insertPrivacy(account, "PUBLIC", "PUBLIC", "PUBLIC", "PUBLIC", "DETAILED");
        befriend(account, newUser());
        befriend(newUser(), account);
        block(account, newUser());
        block(newUser(), account);
        jdbc.update("insert into user_preferences (user_id, locale, theme, reduced_motion, compact_layout, updated_at) "
                + "values (?, 'en', 'DARK', false, false, now())", account);
        jdbc.update("insert into linked_accounts (id, user_id, provider, external_id, active, linked_at, changed_at) "
                + "values (?, ?, 'STEAM', '7656119' || lpad(floor(random() * 1e10)::bigint::text, 10, '0'), true, "
                + "now(), now())", UUID.randomUUID(), account);
        jdbc.update("insert into profile_counters (user_id, review_count, review_changed_at) values (?, 3, now())",
                account);
        assertThat(rowsOf(account)).isPositive();
    }

    private void insertMedia(UUID account, String kind) {
        jdbc.update("""
                insert into user_media (id, user_id, kind, storage_key, content_type, byte_size, width, height, status,
                                        created_at)
                values (?, ?, ?, ?, 'image/png', 1000, 512, 512, 'CURRENT', now())""",
                UUID.randomUUID(), account, kind, UUID.randomUUID().toString().replace("-", ""));
    }

    /** Hesapla ilgili tüm User satırlarının toplamı. */
    private int rowsOf(UUID account) {
        String id = account.toString();
        return jdbc.queryForObject("""
                select (select count(*) from user_profiles where user_id = ?::uuid)
                     + (select count(*) from user_media where user_id = ?::uuid)
                     + (select count(*) from privacy_settings where user_id = ?::uuid)
                     + (select count(*) from social_friendships where user_low_id = ?::uuid or user_high_id = ?::uuid)
                     + (select count(*) from social_blocks where blocker_id = ?::uuid or blocked_id = ?::uuid)
                     + (select count(*) from profile_counters where user_id = ?::uuid)
                     + (select count(*) from user_preferences where user_id = ?::uuid)
                     + (select count(*) from linked_accounts where user_id = ?::uuid)""", Integer.class,
                id, id, id, id, id, id, id, id, id, id);
    }
}
