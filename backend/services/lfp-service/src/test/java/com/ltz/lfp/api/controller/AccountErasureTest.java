package com.ltz.lfp.api.controller;

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

import com.ltz.lfp.IntegrationTestSupport;
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
 * Auth → LFP hesap verisi silme (LFP-18, AUTH-16/17): yalnız Auth'un {@code account:erase} servis token'ıyla, yalnız
 * token'ın bağlı olduğu hesap; idempotent. Ortak silme rehberinin senaryoları (Social/Messaging ile aynı sözleşme).
 */
class AccountErasureTest extends IntegrationTestSupport {

    // ------------------------------------------------------------ veri

    @Test
    void servisTokeniyleHesabinTumIlanlariVeRolleriSilinir204() throws Exception {
        UUID account = newUser();
        seed(account);

        erase(account, serviceTokenValue(account)).andExpect(status().isNoContent());

        assertThat(rowsOf(account)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from lfp_listing_roles r where not exists "
                + "(select 1 from lfp_listings l where l.id = r.listing_id)", Integer.class)).isZero();
    }

    @Test
    void silmeBaskalarininIlanlarinaDokunmaz() throws Exception {
        UUID account = newUser();
        UUID other = newUser();
        seed(account);
        UUID othersListing = openListing(other);

        erase(account, serviceTokenValue(account)).andExpect(status().isNoContent());

        assertThat(listingCount(other)).isEqualTo(1);
        assertThat(listingStatus(othersListing)).isEqualTo("OPEN");
    }

    @Test
    void silmeIdempotentIkinciCagriVeOlmayanHesapDa204() throws Exception {
        UUID account = newUser();
        seed(account);

        erase(account, serviceTokenValue(account)).andExpect(status().isNoContent());
        erase(account, serviceTokenValue(account)).andExpect(status().isNoContent());
        UUID unknown = newUser();
        erase(unknown, serviceTokenValue(unknown)).andExpect(status().isNoContent());
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
    void kullaniciTokeniIcUcuAcamazAdminRoluDahil401() throws Exception {
        UUID account = newUser();
        seed(account);
        int before = rowsOf(account);

        erase(account, accessToken(account, claims -> claims.claim("roles", List.of("ADMIN"))))
                .andExpect(status().isUnauthorized());
        erase(account, accessToken(account, UnaryOperator.identity())).andExpect(status().isUnauthorized());

        assertThat(rowsOf(account)).isEqualTo(before);
    }

    @Test
    void servisTokeniKullaniciUclarindaGecmez401() throws Exception {
        String serviceToken = serviceTokenValue(newUser());

        mockMvc.perform(get(LfpApiPaths.LISTINGS).header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(LfpApiPaths.LISTINGS).header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void yanlisAudienceIssuerVeTur401() throws Exception {
        UUID account = newUser();

        erase(account, serviceToken(account, claims -> claims.audience("social-service")))
                .andExpect(status().isUnauthorized());
        erase(account, serviceToken(account, claims -> claims.audience("ltz-api"))).andExpect(status().isUnauthorized());
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

        erase(account, serviceToken(account, claims -> claims.claim("scope", "blocks:check")))
                .andExpect(status().isForbidden())
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

        erase(account, serviceToken(account, claims -> claims.claim("account_id", UUID.randomUUID().toString())))
                .andExpect(status().isForbidden());
        erase(account, serviceToken(account, claims -> claims.claim("account_id", null)))
                .andExpect(status().isForbidden());
        erase(account, serviceTokenValue(newUser())).andExpect(status().isForbidden());

        assertThat(rowsOf(account)).isEqualTo(before);
    }

    @Test
    void yolVeClaimdeAyniUuidFarkliHarfBuyuklugunde204() throws Exception {
        UUID account = newUser();
        seed(account);

        mockMvc.perform(delete("/internal/v1/accounts/" + account.toString().toUpperCase())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTokenValue(account)))
                .andExpect(status().isNoContent());

        assertThat(rowsOf(account)).isZero();
    }

    @Test
    void fazlaKapsamliTokendeDeEraseKapsamiYeterli() throws Exception {
        UUID account = newUser();
        erase(account, serviceToken(account, claims -> claims.claim("scope", "account:read account:erase")))
                .andExpect(status().isNoContent());
    }

    @Test
    void servisTokeniSozlesmeKurallariOmurSubJtiSuresi() throws Exception {
        UUID account = newUser();
        Instant now = Instant.now();

        erase(account, serviceToken(account, claims -> claims.expirationTime(Date.from(now.plusSeconds(600)))))
                .andExpect(status().isUnauthorized());
        erase(account, serviceToken(account, claims -> claims.issueTime(Date.from(now.minusSeconds(600)))
                .notBeforeTime(Date.from(now.minusSeconds(600))).expirationTime(Date.from(now.minusSeconds(540)))))
                .andExpect(status().isUnauthorized());
        erase(account, serviceToken(account, claims -> claims.subject(UUID.randomUUID().toString())))
                .andExpect(status().isUnauthorized());
        erase(account, serviceToken(account, claims -> claims.jwtID(null))).andExpect(status().isUnauthorized());
        // iat çıkarılarak ömür kuralı aşılamaz: Spring iat'ı exp-1 sayar, bu yüzden bitiş zamanı ayrıca denetlenir.
        erase(account, serviceToken(account, claims -> claims.issueTime(null)
                .expirationTime(Date.from(now.plusSeconds(3600))))).andExpect(status().isUnauthorized());
    }

    @Test
    void imzaSaldirilari401BaskaAnahtarNoneHs256BozukImza() throws Exception {
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

    // ------------------------------------------------------------ yardımcılar

    private static String serviceTokenValue(UUID account) {
        return serviceToken(account, UnaryOperator.identity());
    }

    private ResultActions erase(UUID account, String token) throws Exception {
        return mockMvc.perform(delete(InternalApiPaths.ACCOUNT_ERASURE, account)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    /** Hesabın her durumda ilanı olacak şekilde gerçek akışlarla veri üretir: açık, kapalı ve süresi dolmuş. */
    private void seed(UUID account) throws Exception {
        openListing(account);
        UUID closed = openListing(account);
        closeListing(account, closed).andExpect(status().isOk());
        UUID expired = openListing(account);
        makeOverdue(expired);
        jdbc.update("update lfp_listings set status = 'EXPIRED' where id = ?", expired);
        assertThat(rowsOf(account)).isPositive();
    }

    /** Hesaba ait ilan ve rol satırlarının toplamı. */
    private int rowsOf(UUID account) {
        return jdbc.queryForObject("""
                select (select count(*) from lfp_listings where owner_id = ?)
                     + (select count(*) from lfp_listing_roles r join lfp_listings l on l.id = r.listing_id
                        where l.owner_id = ?)""", Integer.class, account, account);
    }
}
