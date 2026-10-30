package com.ltz.user.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.user.IntegrationTestSupport;

/**
 * Auth → User olay girişi ({@code POST /internal/events}). Zarf ve token, Auth'un {@code HttpAuthEventSender}
 * çıktısıyla aynı biçimdedir: {@code sub=svc:auth-service}, {@code aud=user-service}, {@code scope=events:ingest},
 * {@code account_id=aggregateId}.
 */
class InternalEventTest extends IntegrationTestSupport {

    private static final String USER_REGISTERED = "UserRegistered";
    private static final String STATUS_CHANGED = "AccountStatusChanged";
    private static final String EMAIL_CHANGED = "AccountEmailChanged";

    // ------------------------------------------------------------ UserRegistered

    @Test
    void userRegisteredProfiliAcarVeKullaniciAdiniYazar() throws Exception {
        UUID account = newUser();

        send(registered(UUID.randomUUID(), account, "Oyuncu_01"), eventToken(account, UnaryOperator.identity()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("PROCESSED"));

        assertThat(jdbc.queryForObject("select username_snapshot from user_profiles where user_id = ?", String.class,
                account)).isEqualTo("Oyuncu_01");
        assertThat(jdbc.queryForObject("select username_normalized from user_profiles where user_id = ?",
                String.class, account)).isEqualTo("oyuncu_01");
        assertThat(jdbc.queryForObject("select account_status from user_profiles where user_id = ?", String.class,
                account)).isEqualTo("ACTIVE");
    }

    @Test
    void userRegisteredVarOlanProfildeSadeceKullaniciAdiniDoldurur() throws Exception {
        UUID account = newUser();
        insertProfile(account);

        send(registered(UUID.randomUUID(), account, "oyuncu"), eventToken(account, UnaryOperator.identity()))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select display_name from user_profiles where user_id = ?", String.class,
                account)).isEqualTo("Test Oyuncu");
        assertThat(jdbc.queryForObject("select username_snapshot from user_profiles where user_id = ?", String.class,
                account)).isEqualTo("oyuncu");
    }

    @Test
    void userRegisteredKullaniciAdiGelinceProfilMeUcundaGorunur() throws Exception {
        UUID account = newUser();
        send(registered(UUID.randomUUID(), account, "gorunen"), eventToken(account, UnaryOperator.identity()))
                .andExpect(status().isOk());

        mockMvc.perform(get(UserApiPaths.ME).header(HttpHeaders.AUTHORIZATION, bearer(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("gorunen"));
    }

    @Test
    void userRegisteredBannedOlanHesabiTekrarAktifYapmaz() throws Exception {
        UUID account = newUser();
        // Durum olayı kayıt olayından önce ulaşabilir (yeniden deneme sırası bozulur).
        send(statusChanged(UUID.randomUUID(), account, "BANNED", Instant.now()), eventToken(account, UnaryOperator.identity()))
                .andExpect(status().isOk());

        send(registered(UUID.randomUUID(), account, "yasakli"), eventToken(account, UnaryOperator.identity()))
                .andExpect(status().isOk());

        assertThat(statusOf(account)).isEqualTo("BANNED");
        assertThat(jdbc.queryForObject("select username_snapshot from user_profiles where user_id = ?", String.class,
                account)).isEqualTo("yasakli");
    }

    // ------------------------------------------------------------ tekilleştirme

    @Test
    void ayniEventIdIkinciKezGelirseDuplicate() throws Exception {
        UUID account = newUser();
        UUID eventId = UUID.randomUUID();
        String token = eventToken(account, UnaryOperator.identity());

        send(registered(eventId, account, "tekrar"), token).andExpect(jsonPath("$.outcome").value("PROCESSED"));
        jdbc.update("update user_profiles set display_name = 'Elle' where user_id = ?", account);
        send(registered(eventId, account, "tekrar"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DUPLICATE"));

        assertThat(jdbc.queryForObject("select count(*) from inbox_events where event_id = ?", Integer.class,
                eventId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select display_name from user_profiles where user_id = ?", String.class,
                account)).isEqualTo("Elle");
    }

    @Test
    void eszamanliAyniOlayBirIslenirDigeriDuplicate() throws Exception {
        UUID account = newUser();
        UUID eventId = UUID.randomUUID();
        String token = eventToken(account, UnaryOperator.identity());
        Callable<String> delivery = () -> send(registered(eventId, account, "yaris"), token)
                .andReturn().getResponse().getContentAsString();

        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Future<String>> results = pool.invokeAll(List.of(delivery, delivery, delivery, delivery));
            int processed = 0;
            int duplicates = 0;
            for (Future<String> result : results) {
                String body = result.get();
                processed += body.contains("\"PROCESSED\"") ? 1 : 0;
                duplicates += body.contains("\"DUPLICATE\"") ? 1 : 0;
            }
            assertThat(processed).isEqualTo(1);
            assertThat(duplicates).isEqualTo(3);
        }
        assertThat(profileCount(account)).isEqualTo(1);
    }

    @Test
    void gecersizYukIsleninisSayilmazInboxaYazilmaz() throws Exception {
        UUID account = newUser();
        UUID eventId = UUID.randomUUID();
        String token = eventToken(account, UnaryOperator.identity());

        send(registered(eventId, account, "x"), token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT"));

        assertThat(jdbc.queryForObject("select count(*) from inbox_events where event_id = ?", Integer.class,
                eventId)).isZero();
        assertThat(profileCount(account)).isZero();
        // Aynı olay düzeltilmiş yükle sonra işlenebilir.
        send(registered(eventId, account, "duzgun"), token).andExpect(jsonPath("$.outcome").value("PROCESSED"));
    }

    // ------------------------------------------------------------ AccountStatusChanged

    @Test
    void statusChangedVarOlanProfilinDurumunuGunceller() throws Exception {
        UUID account = newUser();
        insertProfile(account);

        send(statusChanged(UUID.randomUUID(), account, "SUSPENDED", Instant.now()),
                eventToken(account, UnaryOperator.identity())).andExpect(jsonPath("$.outcome").value("PROCESSED"));

        assertThat(statusOf(account)).isEqualTo("SUSPENDED");
    }

    @Test
    void statusChangedEskiOlayYeniDurumuEzmez() throws Exception {
        UUID account = newUser();
        insertProfile(account);
        Instant newer = Instant.now();
        String token = eventToken(account, UnaryOperator.identity());

        send(statusChanged(UUID.randomUUID(), account, "BANNED", newer), token).andExpect(status().isOk());
        send(statusChanged(UUID.randomUUID(), account, "ACTIVE", newer.minusSeconds(60)), token)
                .andExpect(status().isOk());

        assertThat(statusOf(account)).isEqualTo("BANNED");

        send(statusChanged(UUID.randomUUID(), account, "ACTIVE", newer.plusSeconds(60)), token)
                .andExpect(status().isOk());
        assertThat(statusOf(account)).isEqualTo("ACTIVE");
    }

    @Test
    void statusChangedProfilYokkenKisitlayiciDurumKaydedilir() throws Exception {
        UUID account = newUser();

        send(statusChanged(UUID.randomUUID(), account, "BANNED", Instant.now()),
                eventToken(account, UnaryOperator.identity())).andExpect(status().isOk());

        assertThat(statusOf(account)).isEqualTo("BANNED");
    }

    @Test
    void statusChangedProfilYokkenActiveProfilOlusturmaz() throws Exception {
        UUID account = newUser();

        send(statusChanged(UUID.randomUUID(), account, "ACTIVE", Instant.now()),
                eventToken(account, UnaryOperator.identity())).andExpect(status().isOk());

        assertThat(profileCount(account)).isZero();
    }

    @Test
    void statusChangedTanimsizDurumProfiliDegistirmez() throws Exception {
        UUID account = newUser();
        insertProfile(account);

        send(statusChanged(UUID.randomUUID(), account, "SOMETHING_NEW", Instant.now()),
                eventToken(account, UnaryOperator.identity())).andExpect(status().isOk());

        assertThat(statusOf(account)).isEqualTo("ACTIVE");
    }

    // ------------------------------------------------------------ AccountEmailChanged ve bilinmeyen tür

    @Test
    void emailChangedKabulEdilirAmaProfiliDegistirmez() throws Exception {
        UUID account = newUser();
        insertProfile(account);

        send(envelope(UUID.randomUUID(), EMAIL_CHANGED, "auth-service", account, "{\"accountId\":\"%s\"}".formatted(account), 1),
                eventToken(account, UnaryOperator.identity()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("PROCESSED"));

        assertThat(statusOf(account)).isEqualTo("ACTIVE");
    }

    @Test
    void bilinmeyenOlayTuruIgnoredVeKaydedilmez() throws Exception {
        UUID account = newUser();
        UUID eventId = UUID.randomUUID();

        send(envelope(eventId, "SomethingElse", "auth-service", account, "{}", 1),
                eventToken(account, UnaryOperator.identity()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("IGNORED"));

        assertThat(jdbc.queryForObject("select count(*) from inbox_events where event_id = ?", Integer.class,
                eventId)).isZero();
    }

    // ------------------------------------------------------------ doğrulama (400)

    @Test
    void ureticiOlayTuruyleEslesmezse400() throws Exception {
        UUID account = newUser();
        String token = eventToken(account, claims -> claims.subject("svc:admin-service"));

        send(envelope(UUID.randomUUID(), USER_REGISTERED, "admin-service", account,
                "{\"accountId\":\"%s\",\"username\":\"sahte\"}".formatted(account), 1), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVENT_PRODUCER_MISMATCH"));

        assertThat(profileCount(account)).isZero();
    }

    @Test
    void yukteAccountIdAggregateIdIleUyusmazsa400() throws Exception {
        UUID account = newUser();
        UUID other = newUser();

        send(envelope(UUID.randomUUID(), USER_REGISTERED, "auth-service", account,
                "{\"accountId\":\"%s\",\"username\":\"karisik\"}".formatted(other), 1),
                eventToken(account, UnaryOperator.identity())).andExpect(status().isBadRequest());

        assertThat(profileCount(account)).isZero();
        assertThat(profileCount(other)).isZero();
    }

    @Test
    void desteklenmeyenSurum400() throws Exception {
        UUID account = newUser();

        send(envelope(UUID.randomUUID(), USER_REGISTERED, "auth-service", account,
                "{\"accountId\":\"%s\",\"username\":\"surum2\"}".formatted(account), 2),
                eventToken(account, UnaryOperator.identity())).andExpect(status().isBadRequest());
    }

    @Test
    void zarfaTanimsizAlanEklenirse400() throws Exception {
        UUID account = newUser();
        String body = registered(UUID.randomUUID(), account, "fazla").replace("\"payload\"", "\"extra\":1,\"payload\"");

        send(body, eventToken(account, UnaryOperator.identity())).andExpect(status().isBadRequest());
    }

    @Test
    void zarfEksikAlanlarla400() throws Exception {
        UUID account = newUser();

        send("{\"eventId\":\"%s\"}".formatted(UUID.randomUUID()), eventToken(account, UnaryOperator.identity()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void asiriBuyukGovde413() throws Exception {
        UUID account = newUser();
        String bloated = "{\"eventId\":\"%s\",\"pad\":\"%s\"}".formatted(UUID.randomUUID(), "x".repeat(20_000));

        send(bloated, eventToken(account, UnaryOperator.identity())).andExpect(status().isContentTooLarge());
    }

    // ------------------------------------------------------------ kimlik ve yetki

    @Test
    void tokensiz401() throws Exception {
        mockMvc.perform(post(InternalApiPaths.EVENTS).contentType(MediaType.APPLICATION_JSON)
                        .content(registered(UUID.randomUUID(), newUser(), "tokensiz")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void kullaniciTokeni401() throws Exception {
        UUID account = newUser();

        mockMvc.perform(post(InternalApiPaths.EVENTS).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account))
                        .content(registered(UUID.randomUUID(), account, "kullanici")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void yanlisKapsam403SilmeKapsamiOlayGirisineYetmez() throws Exception {
        UUID account = newUser();
        String eraseToken = serviceToken(account, UnaryOperator.identity());

        send(registered(UUID.randomUUID(), account, "kapsam"), eraseToken).andExpect(status().isForbidden());

        assertThat(profileCount(account)).isZero();
    }

    @Test
    void baskaServisAuthAdinaOlayYayinlayamaz403() throws Exception {
        UUID account = newUser();
        String token = eventToken(account, claims -> claims.subject("svc:admin-service"));

        send(registered(UUID.randomUUID(), account, "sahte"), token).andExpect(status().isForbidden());

        assertThat(profileCount(account)).isZero();
    }

    @Test
    void tokenBaskaHesabaBagliysaVeyaBaglidegilse403() throws Exception {
        UUID account = newUser();

        send(registered(UUID.randomUUID(), account, "baska"),
                eventToken(newUser(), UnaryOperator.identity())).andExpect(status().isForbidden());
        send(registered(UUID.randomUUID(), account, "yok"),
                eventToken(account, claims -> claims.claim("account_id", null))).andExpect(status().isForbidden());

        assertThat(profileCount(account)).isZero();
    }

    @Test
    void yanlisAudience401() throws Exception {
        UUID account = newUser();

        send(registered(UUID.randomUUID(), account, "aud"),
                eventToken(account, claims -> claims.audience("community-service"))).andExpect(status().isUnauthorized());
    }

    @Test
    void olayUcuGetIleAcilmaz() throws Exception {
        mockMvc.perform(get(InternalApiPaths.EVENTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + eventToken(newUser(), UnaryOperator.identity())))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ yardımcılar

    private ResultActions send(String body, String token) throws Exception {
        return mockMvc.perform(post(InternalApiPaths.EVENTS)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(body));
    }

    private String statusOf(UUID account) {
        return jdbc.queryForObject("select account_status from user_profiles where user_id = ?", String.class, account);
    }

    private static String registered(UUID eventId, UUID account, String username) {
        return envelope(eventId, USER_REGISTERED, "auth-service", account,
                "{\"accountId\":\"%s\",\"username\":\"%s\"}".formatted(account, username), 1);
    }

    private static String statusChanged(UUID eventId, UUID account, String status, Instant occurredAt) {
        return envelope(eventId, STATUS_CHANGED, "auth-service", account,
                "{\"accountId\":\"%s\",\"status\":\"%s\"}".formatted(account, status), 1, occurredAt);
    }

    private static String envelope(UUID eventId, String type, String producer, UUID aggregate, String payload,
                                   int version) {
        return envelope(eventId, type, producer, aggregate, payload, version, Instant.now());
    }

    private static String envelope(UUID eventId, String type, String producer, UUID aggregate, String payload,
                                   int version, Instant occurredAt) {
        return """
                {"eventId":"%s","eventType":"%s","eventVersion":%d,"occurredAt":"%s","producer":"%s",\
                "aggregateId":"%s","payload":%s}""".formatted(eventId, type, version, occurredAt, producer, aggregate,
                payload);
    }
}
