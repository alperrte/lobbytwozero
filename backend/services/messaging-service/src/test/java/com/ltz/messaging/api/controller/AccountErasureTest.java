package com.ltz.messaging.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.TestImages;
import com.ltz.messaging.infrastructure.messaging.realtime.SseMessageHub;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

/**
 * Auth → Messaging hesap verisi silme (iç uç, docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md). Yalnız
 * Auth'un servis token'ı kabul edilir. Hesabın mesajları satır olarak kalır ama içerikleri silinir ve gönderen anonim
 * kimliğe döner; hesabın tepki, gizleme, okuma, üyelik, yükleme ve outbox izleri kalmaz; başkalarının verisi korunur.
 */
class AccountErasureTest extends IntegrationTestSupport {

    private static final String ANONYMOUS = "00000000-0000-0000-0000-000000000000";
    private static final String ERASURE = "/internal/v1/accounts/{accountId}";

    @Autowired
    private SseMessageHub hub;

    // ------------------------------------------------------------ silme davranışı

    @Test
    void servisTokeniyleHesabinKisiselVerisiSilinir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID aliceText = sendAndGetId(alice, direct, "Gizli metin");
        UUID image = uploadAndGetId(alice, direct, TestImages.png(6, 6), MediaType.IMAGE_PNG_VALUE);
        UUID aliceImage = idOf(sendImage(alice, direct, image, "Foto açıklaması").andExpect(status().isCreated()));
        uploadAndGetId(alice, direct, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE);
        UUID bobText = sendAndGetId(bob, direct, "Bob yazdı");
        react(alice, direct, bobText, "❤️").andExpect(status().isOk());
        react(bob, direct, aliceText, "👍").andExpect(status().isOk());
        deleteMessage(alice, direct, bobText, "me").andExpect(status().isNoContent());
        markRead(alice, direct, 3).andExpect(status().isOk());
        java.util.List<UUID> aliceBlobs = jdbc.queryForList(
                "select blob_id from message_attachments where uploader_id = ?", UUID.class, alice);

        erase(alice, serviceTokenValue(alice)).andExpect(status().isNoContent());

        assertThat(traces(alice)).as("silinen hesabın kimliği hiçbir tabloda kalmamalı").isZero();
        assertThat(jdbc.queryForObject("select count(*) from messages where id in (?, ?) and body is null "
                + "and deleted_at is not null and sender_id = '" + ANONYMOUS + "'", Integer.class, aliceText,
                aliceImage)).isEqualTo(2);
        assertThat(aliceBlobs).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from message_attachments where conversation_id = ?",
                Integer.class, direct)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from attachment_blobs where id = any(?)", Integer.class,
                (Object) aliceBlobs.toArray(new UUID[0]))).as("ek baytları da silinir").isZero();
        assertThat(jdbc.queryForObject("select count(*) from message_reactions where message_id = ?", Integer.class,
                aliceText)).as("silinen mesajın tepkileri de gider").isZero();
        // Karşı tarafın geçmişi ve sırası kalır; silinen hesabın mesajları "silindi" ve anonim görünür.
        history(bob, direct, "?after=0").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].deleted").value(true))
                .andExpect(jsonPath("$.items[0].senderId").value(ANONYMOUS))
                .andExpect(jsonPath("$.items[0].body").isEmpty())
                .andExpect(jsonPath("$.items[1].attachment").isEmpty())
                .andExpect(jsonPath("$.items[2].body").value("Bob yazdı"));
    }

    @Test
    void silmeIdempotentIkinciCagriVeVerisiOlmayanHesap204() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        send(alice, direct, "Merhaba");

        erase(alice, serviceTokenValue(alice)).andExpect(status().isNoContent());
        erase(alice, serviceTokenValue(alice)).andExpect(status().isNoContent());
        UUID unknown = UUID.randomUUID();
        erase(unknown, serviceTokenValue(unknown)).andExpect(status().isNoContent());
    }

    @Test
    void baskalarininVerisineDokunulmaz() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID other = openDirectAndGetId(bob, carol);
        UUID bobMessage = sendAndGetId(bob, other, "Bob → Carol");
        react(carol, other, bobMessage, "😀").andExpect(status().isOk());
        deleteMessage(carol, other, bobMessage, "me").andExpect(status().isNoContent());
        openDirectAndGetId(alice, bob);
        int before = jdbc.queryForObject("select count(*) from messages where conversation_id = ?", Integer.class,
                other);

        erase(alice, serviceTokenValue(alice)).andExpect(status().isNoContent());

        assertThat(traces(bob)).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from messages where conversation_id = ? and body is not null",
                Integer.class, other)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from message_reactions where user_id = ?", Integer.class,
                carol)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from message_hidden_for_users where user_id = ?",
                Integer.class, carol)).isEqualTo(1);
    }

    @Test
    void grupSahibiSilinirseYoneticiSahipOlurBosGrupSilinir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID group = createGroupAndGetId(alice, bob, carol);
        changeRole(alice, group, carol, "ADMIN").andExpect(status().isOk());
        send(alice, group, "Gruba mesaj");
        UUID dave = newUser();
        UUID alone = createGroupAndGetId(alice, dave);
        send(alice, alone, "Tek başıma");
        leave(dave, alone).andExpect(status().isNoContent());

        erase(alice, serviceTokenValue(alice)).andExpect(status().isNoContent());

        assertThat(activeRole(group, carol)).isEqualTo("OWNER");
        assertThat(activeRole(group, bob)).isEqualTo("MEMBER");
        assertThat(jdbc.queryForObject("select count(*) from conversations where id = ?", Integer.class, alone))
                .isZero();
        getConversation(carol, group).andExpect(status().isOk());
        send(carol, group, "Hâlâ çalışıyor");
    }

    @Test
    void birebirdeKarsiTarafSilinmisseYazilamazGecmisOkunur() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        send(alice, direct, "Selam");

        erase(alice, serviceTokenValue(alice)).andExpect(status().isNoContent());

        history(bob, direct, "").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        sendMessage(bob, direct, "c-after", "Orada mısın?")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MESSAGING_NOT_ALLOWED"));
        assertThat(jdbc.queryForObject("select direct_key from conversations where id = ?", String.class, direct))
                .isEqualTo("erased:" + direct);
        // Silinen hesapla yeniden birebir sohbet açılırsa eski sohbet geri gelmez, yenisi açılır.
        assertThat(openDirectAndGetId(bob, alice)).isNotEqualTo(direct);
    }

    @Test
    void outboxOlaylarindanHesapCikarilir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID group = createGroupAndGetId(bob, alice, carol);
        send(alice, group, "Alice'ten");
        send(bob, group, "Bob'tan");

        erase(alice, serviceTokenValue(alice)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from outbox_events where payload::text like ?",
                Integer.class, "%" + alice + "%")).isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where payload->>'senderId' = ? "
                + "and payload->'recipientIds' @> jsonb_build_array(?::text)", Integer.class, bob.toString(),
                carol.toString())).isEqualTo(1);
    }

    @Test
    void hesabinAcikAkislariKapanir() throws Exception {
        UUID alice = newUser();
        openStreamFor(alice);
        assertThat(hub.openStreams(alice)).isEqualTo(1);

        erase(alice, serviceTokenValue(alice)).andExpect(status().isNoContent());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(hub.openStreams(alice)).isZero());
    }

    // ------------------------------------------------------------ token kuralları

    @Test
    void tokensizVeKullaniciTokeniyle401VeriSilinmez() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        send(alice, direct, "Kalmalı");

        mockMvc.perform(delete(ERASURE, alice)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(ERASURE, alice).header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(ERASURE, alice).header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken(alice, claims -> claims.claim("roles", java.util.List.of("ADMIN")))))
                .andExpect(status().isUnauthorized());
        assertThat(traces(alice)).isPositive();
    }

    @Test
    void yanlisAudienceIssuerYaDaTur401() throws Exception {
        UUID alice = newUser();
        erase(alice, serviceToken(alice, claims -> claims.audience("social-service")))
                .andExpect(status().isUnauthorized());
        erase(alice, serviceToken(alice, claims -> claims.audience("ltz-api"))).andExpect(status().isUnauthorized());
        erase(alice, serviceToken(alice, claims -> claims.issuer("evil-issuer"))).andExpect(status().isUnauthorized());
        erase(alice, sign(SIGNING_KEY, ACCESS_TOKEN_TYPE, validServiceClaims(alice).build()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void kapsamEksikYaDaFarkliysa403FazlaKapsamYeterli() throws Exception {
        UUID alice = newUser();
        erase(alice, serviceToken(alice, claims -> claims.claim("scope", "account:read")))
                .andExpect(status().isForbidden());
        erase(alice, serviceToken(alice, claims -> claims.claim("scope", null))).andExpect(status().isForbidden());
        erase(alice, serviceToken(alice, claims -> claims.claim("scope", "account:erase-all")))
                .andExpect(status().isForbidden());
        erase(alice, serviceToken(alice, claims -> claims.claim("scope", "account:read account:erase")))
                .andExpect(status().isNoContent());
    }

    @Test
    void baskaServisinTokeniYaDaBaskaHesabaBagliToken403VeriSilinmez() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        send(alice, direct, "Kalmalı");

        erase(alice, serviceToken(alice, claims -> claims.subject("svc:admin-service")))
                .andExpect(status().isForbidden());
        erase(alice, serviceToken(alice, claims -> claims.subject("svc:auth-service-evil")))
                .andExpect(status().isForbidden());
        erase(alice, serviceToken(alice, claims -> claims.claim("account_id", UUID.randomUUID().toString())))
                .andExpect(status().isForbidden());
        erase(alice, serviceToken(alice, claims -> claims.claim("account_id", null)))
                .andExpect(status().isForbidden());
        erase(alice, serviceTokenValue(newUser())).andExpect(status().isForbidden());

        assertThat(traces(alice)).isPositive();
    }

    @Test
    void omurSuresiJtiVeIatKurallari401() throws Exception {
        UUID alice = newUser();
        Instant now = Instant.now();
        erase(alice, serviceToken(alice, claims -> claims.expirationTime(Date.from(now.plusSeconds(600)))))
                .andExpect(status().isUnauthorized());
        erase(alice, serviceToken(alice, claims -> claims.issueTime(Date.from(now.minusSeconds(600)))
                .notBeforeTime(Date.from(now.minusSeconds(600))).expirationTime(Date.from(now.minusSeconds(540)))))
                .andExpect(status().isUnauthorized());
        erase(alice, serviceToken(alice, claims -> claims.subject(UUID.randomUUID().toString())))
                .andExpect(status().isUnauthorized());
        erase(alice, serviceToken(alice, claims -> claims.jwtID(null))).andExpect(status().isUnauthorized());
        erase(alice, serviceToken(alice, claims -> claims.issueTime(null)
                .expirationTime(Date.from(now.plusSeconds(3600))))).andExpect(status().isUnauthorized());
    }

    @Test
    void imzaSaldirilari401() throws Exception {
        UUID alice = newUser();
        RSAKey attacker = new RSAKeyGenerator(2048).keyID(SIGNING_KEY.getKeyID()).generate();
        JWTClaimsSet claims = validServiceClaims(alice).build();

        erase(alice, sign(attacker, SERVICE_TOKEN_TYPE, claims)).andExpect(status().isUnauthorized());
        erase(alice, new PlainJWT(claims).serialize()).andExpect(status().isUnauthorized());
        SignedJWT hmac = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(SIGNING_KEY.getKeyID())
                .type(SERVICE_TOKEN_TYPE).build(), claims);
        hmac.sign(new MACSigner(SIGNING_KEY.toPublicJWK().toRSAPublicKey().getEncoded()));
        erase(alice, hmac.serialize()).andExpect(status().isUnauthorized());
        String token = serviceTokenValue(alice);
        erase(alice, token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void servisTokeniKullaniciUclarindaGecmez401() throws Exception {
        String token = "Bearer " + serviceTokenValue(newUser());
        mockMvc.perform(get(MessagingApiPaths.CONVERSATIONS).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(MessagingApiPaths.STREAM).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void digerIcYollarVeYontemlerKapali403() throws Exception {
        UUID alice = newUser();
        String token = "Bearer " + serviceTokenValue(alice);
        mockMvc.perform(get(ERASURE, alice).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ERASURE, alice).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/v1/anything").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void gecersizKimlik400BuyukHarfliKimlik204() throws Exception {
        mockMvc.perform(delete("/internal/v1/accounts/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTokenValue(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
        UUID alice = newUser();
        mockMvc.perform(delete("/internal/v1/accounts/" + alice.toString().toUpperCase(java.util.Locale.ROOT))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTokenValue(alice)))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------ yardımcılar

    private static String serviceTokenValue(UUID account) {
        return serviceToken(account, UnaryOperator.identity());
    }

    private ResultActions erase(UUID account, String token) throws Exception {
        return mockMvc.perform(delete(ERASURE, account).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    /** Hesabın kimliğinin geçtiği satır sayısı (tüm kişisel veri sütunları ve outbox yükü). */
    private int traces(UUID account) {
        String id = account.toString();
        return jdbc.queryForObject("""
                select (select count(*) from messages where sender_id = ?::uuid or deleted_by = ?::uuid)
                     + (select count(*) from conversations where created_by = ?::uuid or position(? in direct_key) > 0)
                     + (select count(*) from conversation_members where user_id = ?::uuid or removed_by = ?::uuid)
                     + (select count(*) from message_reactions where user_id = ?::uuid)
                     + (select count(*) from message_hidden_for_users where user_id = ?::uuid)
                     + (select count(*) from message_read_ranges where user_id = ?::uuid)
                     + (select count(*) from message_attachments where uploader_id = ?::uuid)
                     + (select count(*) from rate_limit_windows where user_id = ?::uuid)
                     + (select count(*) from stream_relay_events where payload like ?)
                     + (select count(*) from outbox_events where payload::text like ?)""", Integer.class,
                id, id, id, id, id, id, id, id, id, id, id, "%" + id + "%", "%" + id + "%");
    }
}
