package com.ltz.social.api.controller;

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

import com.jayway.jsonpath.JsonPath;
import com.ltz.social.IntegrationTestSupport;
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
 * Auth → Social hesap verisi silme (iç uç). Yalnız Auth'un imzaladığı, {@code scope=account:erase} taşıyan servis
 * token'ı ({@code svc+jwt}) kabul edilir; kullanıcı token'ı kabul edilmez ve tersi de geçerlidir.
 */
class AccountErasureTest extends IntegrationTestSupport {

    // ------------------------------------------------------------ silme davranışı

    @Test
    void servisTokeniyleHesabinTumVerisiSilinir_204() throws Exception {
        UUID account = newUser();
        Seed seed = seed(account);

        erase(account, serviceTokenValue(account)).andExpect(status().isNoContent());

        assertThat(rowsOf(account)).as("silinen hesabın hiçbir verisi kalmamalı").isZero();
        assertThat(jdbc.queryForObject("select count(*) from friend_requests where id = ?", Integer.class,
                seed.sentRequest())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from friend_requests where id = ?", Integer.class,
                seed.receivedRequest())).isZero();
    }

    @Test
    void silme_baskalarininIlgisizVerisineDokunmaz() throws Exception {
        UUID account = newUser();
        seed(account);
        UUID friendA = newUser();
        UUID friendB = newUser();
        UUID bystander = newUser();
        UUID unrelatedRequest = sendRequestAndGetId(friendA, friendB);
        befriend(bystander, newUser());
        block(bystander, newUser()).andExpect(status().isCreated());
        int before = rowsOf(bystander) + rowsOf(friendA) + rowsOf(friendB);

        erase(account, serviceTokenValue(account)).andExpect(status().isNoContent());

        assertThat(rowsOf(bystander) + rowsOf(friendA) + rowsOf(friendB)).isEqualTo(before);
        assertThat(requestStatus(unrelatedRequest)).isEqualTo("PENDING");
    }

    @Test
    void silmeIdempotent_ikinciCagriVeOlmayanHesapDa204() throws Exception {
        UUID account = newUser();
        seed(account);

        erase(account, serviceTokenValue(account)).andExpect(status().isNoContent());
        erase(account, serviceTokenValue(account)).andExpect(status().isNoContent());
        UUID unknown = UUID.randomUUID();
        erase(unknown, serviceTokenValue(unknown)).andExpect(status().isNoContent());

        assertThat(rowsOf(account)).isZero();
    }

    @Test
    void silinenHesabinArkadaslarinaAitKendiVerisiKorunur() throws Exception {
        UUID account = newUser();
        UUID friend = newUser();
        befriend(account, friend);
        UUID friendsOwnRequest = sendRequestAndGetId(friend, newUser());
        String friendsCode = codeOf(friend);

        erase(account, serviceTokenValue(account)).andExpect(status().isNoContent());

        assertThat(areFriends(account, friend)).isFalse();
        assertThat(requestStatus(friendsOwnRequest)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from friend_invite_codes where code = ?", Integer.class,
                friendsCode)).isEqualTo(1);
    }

    @Test
    void gecersizHesapKimligi400() throws Exception {
        mockMvc.perform(delete("/internal/v1/accounts/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTokenValue(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    // ------------------------------------------------------------ kimlik ve yetki

    @Test
    void tokensizCagri401_veri_silinmez() throws Exception {
        UUID account = newUser();
        seed(account);
        int before = rowsOf(account);

        mockMvc.perform(delete(InternalApiPaths.ACCOUNT_ERASURE, account))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(rowsOf(account)).isEqualTo(before);
    }

    @Test
    void kullaniciTokeniIcUcuAcamaz_ADMIN_rolu_dahil_401() throws Exception {
        UUID account = newUser();
        seed(account);
        int before = rowsOf(account);
        String adminUserToken = accessToken(account, claims -> claims.claim("roles", List.of("ADMIN")));

        erase(account, adminUserToken).andExpect(status().isUnauthorized());
        erase(account, accessToken(account, UnaryOperator.identity())).andExpect(status().isUnauthorized());

        assertThat(rowsOf(account)).isEqualTo(before);
    }

    @Test
    void servisTokeniKullaniciUclarindaGecmez_401() throws Exception {
        String serviceToken = serviceTokenValue(newUser());

        mockMvc.perform(get(SocialApiPaths.FRIENDS).header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(SocialApiPaths.BLOCKS).header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void yanlisAudienceIssuerVeTur401() throws Exception {
        UUID account = newUser();

        erase(account, serviceToken(account, claims -> claims.audience("messaging-service"))).andExpect(status().isUnauthorized());
        erase(account, serviceToken(account, claims -> claims.audience("ltz-api"))).andExpect(status().isUnauthorized());
        erase(account, serviceToken(account, claims -> claims.issuer("evil-issuer"))).andExpect(status().isUnauthorized());
        erase(account, sign(SIGNING_KEY, JOSEObjectType.JWT, validServiceClaims(account).build()))
                .andExpect(status().isUnauthorized());
        erase(account, sign(SIGNING_KEY, ACCESS_TOKEN_TYPE, validServiceClaims(account).build()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void kapsamEksikVeyaFarkliysa403_veriSilinmez() throws Exception {
        UUID account = newUser();
        seed(account);
        int before = rowsOf(account);

        erase(account, serviceToken(account, claims -> claims.claim("scope", "account:read"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        erase(account, serviceToken(account, claims -> claims.claim("scope", null))).andExpect(status().isForbidden());
        erase(account, serviceToken(account, claims -> claims.claim("scope", "account:erase-all")))
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

    @Test
    void servisTokeniSozlesmeKurallari_omurSubJtiSuresi() throws Exception {
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
        erase(account, serviceToken(account, claims -> claims.issueTime(null).expirationTime(Date.from(now.plusSeconds(3600)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void imzaSaldirilari401_baskaAnahtarNoneHs256BozukImza() throws Exception {
        UUID account = newUser();
        seed(account);
        int before = rowsOf(account);
        RSAKey attacker = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        JWTClaimsSet claims = validServiceClaims(account).build();

        erase(account, sign(attacker, SERVICE_TOKEN_TYPE, claims)).andExpect(status().isUnauthorized());
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

    @Test
    void digerIcYollarVeYontemlerKapali_servisTokeniyleDe() throws Exception {
        UUID account = newUser();
        String bearer = "Bearer " + serviceTokenValue(account);

        mockMvc.perform(get(InternalApiPaths.ACCOUNT_ERASURE, account).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(InternalApiPaths.ACCOUNT_ERASURE, account).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/v1/anything").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ yardımcılar

    private static String serviceTokenValue(UUID account) {
        return serviceToken(account, UnaryOperator.identity());
    }

    private ResultActions erase(UUID account, String token) throws Exception {
        return mockMvc.perform(delete(InternalApiPaths.ACCOUNT_ERASURE, account)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String codeOf(UUID owner) throws Exception {
        return JsonPath.read(mockMvc.perform(post(SocialApiPaths.INVITE_CODE)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))).andReturn().getResponse().getContentAsString(),
                "$.code");
    }

    /** Hesabın her tabloda verisi olacak şekilde gerçek akışlarla veri üretir. */
    private Seed seed(UUID account) throws Exception {
        UUID friend = newUser();
        befriend(account, friend);
        UUID sent = sendRequestAndGetId(account, newUser());
        UUID received = sendRequestAndGetId(newUser(), account);
        block(account, newUser()).andExpect(status().isCreated());
        block(newUser(), account).andExpect(status().isCreated());
        codeOf(account);
        assertThat(rowsOf(account)).isPositive();
        return new Seed(sent, received);
    }

    /** Hesapla ilgili tüm Social satırlarının toplamı. */
    private int rowsOf(UUID account) {
        String id = account.toString();
        return jdbc.queryForObject("""
                select (select count(*) from friend_requests where sender_id = ?::uuid or receiver_id = ?::uuid)
                     + (select count(*) from friendships where user_low_id = ?::uuid or user_high_id = ?::uuid)
                     + (select count(*) from user_blocks where blocker_id = ?::uuid or blocked_id = ?::uuid)
                     + (select count(*) from friend_invite_codes where user_id = ?::uuid)
                     + (select count(*) from outbox_events where payload ->> 'senderId' = ? or payload ->> 'receiverId' = ?
                        or payload ->> 'userLowId' = ? or payload ->> 'userHighId' = ?
                        or payload ->> 'blockerId' = ? or payload ->> 'blockedId' = ?)""", Integer.class,
                id, id, id, id, id, id, id, id, id, id, id, id, id);
    }

    private record Seed(UUID sentRequest, UUID receivedRequest) {
    }
}
