package com.ltz.performance.api.controller;

import com.ltz.performance.application.service.PerformanceAccountErasureService;
import com.ltz.performance.infrastructure.persistence.PostgresIntegrationSupport;
import com.ltz.performance.infrastructure.security.PerformanceTestAuth;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gerçek Auth issuer/JWKS (üretim kaynaklarından derlenmiş), Performance HTTP güvenliği, servisler ve geçici
 * PostgreSQL. Cihaz listesi ({@code GET /api/v1/auth/devices}) YEREL SAPMADIR; performance-service hedefli cihaz
 * token'ı yerel imzalıdır (Auth bugün yalnız aud=hardware-service üretir, REQ-01-A). Bu test, dağıtılmış Auth ile
 * uçtan uca entegrasyon DEĞİLDİR.
 */
@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
@AutoConfigureMockMvc
class PerformanceSyncIntegrationTest extends PostgresIntegrationSupport {
    static final String CONSENT = "/api/v1/performance/sync-consent";
    static final String DEVICES = "/api/v1/performance/devices";
    static final String BATCHES = "/api/v1/performance/sync/batches";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PerformanceAccountErasureService erasure;

    // ---------- yardımcılar ----------

    static String user(UUID account) throws Exception { return PerformanceTestAuth.AUTH.token(account, "PLAYER"); }

    static String bearer(String token) { return "Bearer " + token; }

    String batchBody(Instant sentAt, int count) {
        return "{\"schemaVersion\":1,\"clientSentAt\":\"" + sentAt + "\",\"sampleCount\":" + count + "}";
    }

    MockHttpServletRequestBuilder sync(String token, UUID key, String body) {
        return post(BATCHES).header("Authorization", bearer(token)).header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** Hesap + onay + Auth'ta cihaz + bağlama; bağ kimliğini döner. */
    UUID prepare(UUID account, UUID device) throws Exception {
        PerformanceTestAuth.authDevices(account, device, "Masaüstü PC");
        mvc.perform(put(CONSENT).header("Authorization", bearer(user(account)))).andExpect(status().isOk());
        MvcResult linked = mvc.perform(post(DEVICES).header("Authorization", bearer(user(account)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\":\"" + device + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return UUID.fromString(json.readTree(linked.getResponse().getContentAsString()).get("id").asString());
    }

    // ---------- kimlik doğrulama / yetkilendirme ----------

    @Test
    void anonymousCallsAreRejectedWith401ProblemDetail() throws Exception {
        for (var request : List.of(get(CONSENT), put(CONSENT), delete(CONSENT), get(DEVICES),
                post(DEVICES).contentType(MediaType.APPLICATION_JSON).content("{}"),
                post(BATCHES).contentType(MediaType.APPLICATION_JSON).content("{}"))) {
            mvc.perform(request).andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
    }

    @Test
    void unknownPathsAreDeniedByDefault() throws Exception {
        UUID account = UUID.randomUUID();
        mvc.perform(get("/api/v1/performance/unknown").header("Authorization", bearer(user(account))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void realAuthDeviceTokenForAnotherAudienceCannotSync() throws Exception {
        UUID account = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        prepare(account, device);
        // Gerçek Auth cihaz token'ı bugün aud=hardware-service: Performance bunu REDDETMELİ (REQ-01-A sonuna kadar).
        String hardwareAudienceToken = PerformanceTestAuth.AUTH.deviceToken(device, account);
        mvc.perform(sync(hardwareAudienceToken, UUID.randomUUID(), batchBody(Instant.now(), 1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userTokenCannotCallDeviceSyncAndDeviceTokenCannotCallUserRoutes() throws Exception {
        UUID account = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        mvc.perform(sync(user(account), UUID.randomUUID(), batchBody(Instant.now(), 1)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(CONSENT).header("Authorization",
                        bearer(PerformanceTestAuth.performanceDeviceToken(device, account))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedAndWrongKeyTokensAreRejected() throws Exception {
        UUID account = UUID.randomUUID();
        String token = user(account);
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");
        mvc.perform(get(CONSENT).header("Authorization", bearer(tampered))).andExpect(status().isUnauthorized());
        var foreign = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID(PerformanceTestAuth.AUTH.key().getKeyID())
                .generate();
        String forged = PerformanceTestAuth.sign(foreign, new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(foreign.getKeyID()).type(new JOSEObjectType("dev+jwt")).build(), java.util.Map.of(
                "iss", "ltz-auth", "sub", UUID.randomUUID().toString(), "aud", List.of("performance-service"),
                "account_id", account.toString(), "scope", "performance:sync:write", "jti", UUID.randomUUID().toString(),
                "iat", Instant.now().getEpochSecond(), "nbf", Instant.now().getEpochSecond(),
                "exp", Instant.now().plusSeconds(60).getEpochSecond()));
        mvc.perform(sync(forged, UUID.randomUUID(), batchBody(Instant.now(), 1))).andExpect(status().isUnauthorized());
    }

    @Test
    void deviceTokenWithWrongScopeOrLongLifetimeIsRejected() throws Exception {
        UUID account = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        String wrongScope = PerformanceTestAuth.performanceDeviceToken(device, account, c -> c.put("scope", "hardware:sync:write"));
        String longLived = PerformanceTestAuth.performanceDeviceToken(device, account,
                c -> c.put("exp", Instant.now().plusSeconds(3600).getEpochSecond()));
        String multiAudience = PerformanceTestAuth.performanceDeviceToken(device, account,
                c -> c.put("aud", List.of("performance-service", "hardware-service")));
        for (String token : List.of(wrongScope, longLived, multiAudience)) {
            mvc.perform(sync(token, UUID.randomUUID(), batchBody(Instant.now(), 1))).andExpect(status().isUnauthorized());
        }
    }

    // ---------- onay (consent) ----------

    @Test
    void consentLifecycleIsPrivateAndIdempotent() throws Exception {
        UUID account = UUID.randomUUID();
        String token = bearer(user(account));
        mvc.perform(get(CONSENT).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.granted").value(false));
        mvc.perform(put(CONSENT).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.granted").value(true));
        mvc.perform(put(CONSENT).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.granted").value(true));
        mvc.perform(delete(CONSENT).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.granted").value(false)).andExpect(jsonPath("$.revokedAt").isNotEmpty());
        mvc.perform(delete(CONSENT).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.granted").value(false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_consents WHERE user_id=?", Integer.class, account))
                .isEqualTo(1);
    }

    // ---------- cihaz bağlama ----------

    @Test
    void linkRequiresDeviceInCallersOwnAuthList() throws Exception {
        UUID account = UUID.randomUUID();
        UUID foreignDevice = UUID.randomUUID();
        PerformanceTestAuth.authDevices(UUID.randomUUID(), foreignDevice, "Başkasının cihazı");
        mvc.perform(post(DEVICES).header("Authorization", bearer(user(account)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\":\"" + foreignDevice + "\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PERFORMANCE_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM device_links", Integer.class)).isZero();
    }

    @Test
    void linkIsIdempotentListedAndDisableIsOwnerOnly() throws Exception {
        UUID account = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        UUID linkId = prepare(account, device);
        // tekrar bağlama aynı kaydı döner
        mvc.perform(post(DEVICES).header("Authorization", bearer(user(account)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\":\"" + device + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(linkId.toString()));
        mvc.perform(get(DEVICES).header("Authorization", bearer(user(account)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].status").value("ACTIVE"));
        // IDOR: başka hesap bu bağı devre dışı bırakamaz ve varlığını öğrenemez
        mvc.perform(delete(DEVICES + "/" + linkId).header("Authorization", bearer(user(UUID.randomUUID()))))
                .andExpect(status().isNotFound());
        mvc.perform(delete(DEVICES + "/" + linkId).header("Authorization", bearer(user(account))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
        mvc.perform(delete(DEVICES + "/" + linkId).header("Authorization", bearer(user(account))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    void sameDeviceCannotBeLinkedByAnotherAccount() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        prepare(owner, device);
        UUID other = UUID.randomUUID();
        PerformanceTestAuth.authDevices(other, device, "Aynı kimlik");
        mvc.perform(post(DEVICES).header("Authorization", bearer(user(other)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\":\"" + device + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEVICE_LINK_CONFLICT"));
    }

    @Test
    void activeLinkCapacityIsFive() throws Exception {
        UUID account = UUID.randomUUID();
        for (int i = 0; i < 5; i++) prepareLinkOnly(account, UUID.randomUUID());
        UUID sixth = UUID.randomUUID();
        PerformanceTestAuth.authDevices(account, sixth, "Altıncı");
        mvc.perform(post(DEVICES).header("Authorization", bearer(user(account)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\":\"" + sixth + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEVICE_LINK_LIMIT_REACHED"));
    }

    private void prepareLinkOnly(UUID account, UUID device) throws Exception {
        PerformanceTestAuth.authDevices(account, device, "Cihaz");
        mvc.perform(post(DEVICES).header("Authorization", bearer(user(account)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\":\"" + device + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void authOutageOrMalformedAnswerFailsClosedWith503() throws Exception {
        UUID account = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        PerformanceTestAuth.authDevices(account, device, "Cihaz");
        for (var forced : List.<Object[]>of(new Object[]{500, null}, new Object[]{200, "{\"not\":\"array\"}"},
                new Object[]{200, "<<<bozuk"})) {
            PerformanceTestAuth.forceDeviceListStatus(account, (Integer) forced[0], (String) forced[1]);
            mvc.perform(post(DEVICES).header("Authorization", bearer(user(account)))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\":\"" + device + "\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "30"))
                    .andExpect(jsonPath("$.code").value("AUTH_UNAVAILABLE"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM device_links", Integer.class)).isZero();
    }

    // ---------- senkronizasyon ----------

    @Test
    void syncRequiresConsentAndActiveLink() throws Exception {
        UUID account = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        String token = PerformanceTestAuth.performanceDeviceToken(device, account);
        // onay yok
        mvc.perform(sync(token, UUID.randomUUID(), batchBody(Instant.now(), 1)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SYNC_CONSENT_REQUIRED"));
        // onay var ama bağ yok
        mvc.perform(put(CONSENT).header("Authorization", bearer(user(account)))).andExpect(status().isOk());
        mvc.perform(sync(token, UUID.randomUUID(), batchBody(Instant.now(), 1)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEVICE_NOT_LINKED"));
    }

    @Test
    void syncAcceptsReplaysAndDetectsKeyReuse() throws Exception {
        UUID account = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        prepare(account, device);
        String token = PerformanceTestAuth.performanceDeviceToken(device, account);
        UUID key = UUID.randomUUID();
        Instant sentAt = Instant.now().minusSeconds(5);
        MvcResult first = mvc.perform(sync(token, key, batchBody(sentAt, 7))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.replayed").value(false)).andExpect(jsonPath("$.sampleCount").value(7)).andReturn();
        JsonNode created = json.readTree(first.getResponse().getContentAsString());
        // aynı anahtar + aynı içerik → aynı makbuz, replayed=true, ek kayıt yok
        mvc.perform(sync(PerformanceTestAuth.performanceDeviceToken(device, account), key, batchBody(sentAt, 7)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.batchId").value(created.get("batchId").asString()));
        // aynı anahtar + farklı içerik → 409
        mvc.perform(sync(token, key, batchBody(sentAt, 8))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_batches", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_sync_at IS NOT NULL FROM device_links WHERE auth_device_id=?",
                Boolean.class, device)).isTrue();
    }

    @Test
    void syncRejectsInvalidInput() throws Exception {
        UUID account = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        prepare(account, device);
        String token = PerformanceTestAuth.performanceDeviceToken(device, account);
        mvc.perform(sync(token, UUID.randomUUID(), batchBody(Instant.now().plusSeconds(3600), 1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SYNC_CLIENT_TIME_INVALID"));
        mvc.perform(sync(token, UUID.randomUUID(), batchBody(Instant.now().minusSeconds(3 * 86400), 1)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(sync(token, UUID.randomUUID(), batchBody(Instant.now(), 501))).andExpect(status().isBadRequest());
        mvc.perform(sync(token, UUID.randomUUID(), batchBody(Instant.now(), -1))).andExpect(status().isBadRequest());
        mvc.perform(sync(token, UUID.randomUUID(), "{\"schemaVersion\":2,\"clientSentAt\":\"" + Instant.now()
                + "\",\"sampleCount\":1}")).andExpect(status().isBadRequest());
        mvc.perform(sync(token, UUID.randomUUID(), "{\"schemaVersion\":1,\"clientSentAt\":\"" + Instant.now()
                + "\",\"sampleCount\":1,\"extra\":true}")).andExpect(status().isBadRequest());
        mvc.perform(post(BATCHES).header("Authorization", bearer(token)).header("Idempotency-Key", "not-a-uuid")
                .contentType(MediaType.APPLICATION_JSON).content(batchBody(Instant.now(), 1)))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BATCHES).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(batchBody(Instant.now(), 1)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_batches", Integer.class)).isZero();
    }

    @Test
    void oversizedBodyIsRejectedBefore413Processing() throws Exception {
        UUID account = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        prepare(account, device);
        String token = PerformanceTestAuth.performanceDeviceToken(device, account);
        String huge = "{\"schemaVersion\":1,\"clientSentAt\":\"" + Instant.now() + "\",\"sampleCount\":1,\"pad\":\""
                + "x".repeat(20_000) + "\"}";
        mvc.perform(sync(token, UUID.randomUUID(), huge)).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("REQUEST_TOO_LARGE"));
    }

    @Test
    void revokedConsentDisabledLinkAndOtherAccountsLinkBlockSync() throws Exception {
        UUID account = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        UUID linkId = prepare(account, device);
        String token = PerformanceTestAuth.performanceDeviceToken(device, account);
        mvc.perform(sync(token, UUID.randomUUID(), batchBody(Instant.now(), 1))).andExpect(status().isOk());
        // başka hesabın id'siyle imzalı token: bağ başkasına ait → DEVICE_NOT_LINKED (sahiplik sızıntısı yok)
        UUID intruder = UUID.randomUUID();
        mvc.perform(put(CONSENT).header("Authorization", bearer(user(intruder)))).andExpect(status().isOk());
        mvc.perform(sync(PerformanceTestAuth.performanceDeviceToken(device, intruder), UUID.randomUUID(),
                batchBody(Instant.now(), 1))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEVICE_NOT_LINKED"));
        // bağ devre dışı → sync engellenir (iptal edilmiş cihaz)
        mvc.perform(delete(DEVICES + "/" + linkId).header("Authorization", bearer(user(account)))).andExpect(status().isOk());
        mvc.perform(sync(token, UUID.randomUUID(), batchBody(Instant.now(), 1))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEVICE_NOT_LINKED"));
        // yeniden bağla, ardından onayı geri çek
        mvc.perform(post(DEVICES).header("Authorization", bearer(user(account)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\":\"" + device + "\"}")).andExpect(status().isOk());
        mvc.perform(sync(token, UUID.randomUUID(), batchBody(Instant.now(), 1))).andExpect(status().isOk());
        mvc.perform(delete(CONSENT).header("Authorization", bearer(user(account)))).andExpect(status().isOk());
        mvc.perform(sync(token, UUID.randomUUID(), batchBody(Instant.now(), 1))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SYNC_CONSENT_REQUIRED"));
    }

    @Test
    void concurrentSameKeyCreatesSingleBatch() throws Exception {
        UUID account = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        prepare(account, device);
        UUID key = UUID.randomUUID();
        Instant sentAt = Instant.now().minusSeconds(2);
        int threads = 6;
        try (var pool = Executors.newFixedThreadPool(threads)) {
            List<Callable<Integer>> calls = new ArrayList<>();
            List<String> tokens = new ArrayList<>();
            for (int i = 0; i < threads; i++) tokens.add(PerformanceTestAuth.performanceDeviceToken(device, account));
            for (String token : tokens) {
                calls.add(() -> mvc.perform(sync(token, key, batchBody(sentAt, 3))).andReturn().getResponse().getStatus());
            }
            List<Integer> statuses = new ArrayList<>();
            for (var future : pool.invokeAll(calls)) statuses.add(future.get());
            assertThat(statuses).allMatch(code -> code == 200);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_batches", Integer.class)).isEqualTo(1);
    }

    @Test
    void liveBatchCapacityPerDeviceIsEnforced() throws Exception {
        UUID account = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        UUID linkId = prepare(account, device);
        // 500 canlı makbuzu doğrudan ekle (HTTP hız sınırını aşmadan kapasite kuralını sınar)
        jdbc.update("""
                INSERT INTO sync_batches (id, device_link_id, user_id, idempotency_key, request_hash, schema_version,
                                          sample_count, client_sent_at, received_at, expires_at)
                SELECT gen_random_uuid(), ?, ?, gen_random_uuid(), repeat('a', 64), 1, 1, now(), now(), now() + interval '1 hour'
                FROM generate_series(1, 500)
                """, linkId, account);
        mvc.perform(sync(PerformanceTestAuth.performanceDeviceToken(device, account), UUID.randomUUID(),
                batchBody(Instant.now(), 1))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYNC_BATCH_LIMIT_REACHED"));
    }

    // ---------- iç (service-to-service) uçlar ve hesap silme ----------

    @Test
    void accountErasureRequiresAuthServiceTokenBoundToAccount() throws Exception {
        UUID account = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        prepare(account, device);
        String path = "/internal/v1/accounts/" + account;
        mvc.perform(delete(path)).andExpect(status().isUnauthorized());
        mvc.perform(delete(path).header("Authorization", bearer(user(account)))).andExpect(status().isUnauthorized());
        // başka servis audience'ı ve başka hesaba bağlı token reddedilir
        String wrongAudience = PerformanceTestAuth.AUTH.serviceToken("hardware-service", "account:erase", account);
        mvc.perform(delete(path).header("Authorization", bearer(wrongAudience))).andExpect(status().isUnauthorized());
        String otherAccount = PerformanceTestAuth.AUTH.serviceToken("performance-service", "account:erase", UUID.randomUUID());
        mvc.perform(delete(path).header("Authorization", bearer(otherAccount))).andExpect(status().isForbidden());
        String wrongScope = PerformanceTestAuth.AUTH.serviceToken("performance-service", "devices:verify", account);
        mvc.perform(delete(path).header("Authorization", bearer(wrongScope))).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM device_links WHERE user_id=?", Integer.class, account)).isEqualTo(1);

        String valid = PerformanceTestAuth.AUTH.serviceToken("performance-service", "account:erase", account);
        mvc.perform(delete(path).header("Authorization", bearer(valid))).andExpect(status().isNoContent());
        for (String table : List.of("sync_consents", "device_links", "sync_batches")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id=?", Integer.class, account))
                    .as(table).isZero();
        }
        // idempotent: ikinci silme de 204
        mvc.perform(delete(path).header("Authorization",
                        bearer(PerformanceTestAuth.AUTH.serviceToken("performance-service", "account:erase", account))))
                .andExpect(status().isNoContent());
        // silinen hesap yeni veri yazamaz (17 dk silme çiti)
        mvc.perform(put(CONSENT).header("Authorization", bearer(user(account)))).andExpect(status().is4xxClientError());
    }

    @Test
    void erasureServiceCleansOnlyTheTargetAccount() throws Exception {
        UUID target = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        UUID targetDevice = UUID.randomUUID();
        UUID bystanderDevice = UUID.randomUUID();
        UUID targetLink = prepare(target, targetDevice);
        prepare(bystander, bystanderDevice);
        mvc.perform(sync(PerformanceTestAuth.performanceDeviceToken(targetDevice, target), UUID.randomUUID(),
                batchBody(Instant.now(), 1))).andExpect(status().isOk());
        assertThat(erasure.erase(target)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_batches WHERE device_link_id=?", Integer.class, targetLink))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM device_links WHERE user_id=?", Integer.class, bystander))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_consents WHERE user_id=?", Integer.class, bystander))
                .isEqualTo(1);
    }

    @Test
    void activeDeviceEndpointIsHardwareBoundAndOwnerChecked() throws Exception {
        UUID account = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        UUID linkId = prepare(account, device);
        String path = "/internal/v1/accounts/" + account + "/devices/" + device;
        // Gerçek Auth servis token'ı yalnız sub=svc:auth-service üretir → Hardware'e özel uç reddedilir (REQ-01-C)
        String authSubject = PerformanceTestAuth.AUTH.serviceToken("performance-service", "devices:verify", account);
        mvc.perform(get(path).header("Authorization", bearer(authSubject))).andExpect(status().isForbidden());
        // Sözleşme biçimi (yerel imza, sub=svc:hardware-service)
        String hardware = serviceToken("svc:hardware-service", "devices:verify", account);
        mvc.perform(get(path).header("Authorization", bearer(hardware))).andExpect(status().isNoContent());
        mvc.perform(get("/internal/v1/accounts/" + account + "/devices/" + UUID.randomUUID())
                .header("Authorization", bearer(hardware))).andExpect(status().isNotFound());
        mvc.perform(delete(DEVICES + "/" + linkId).header("Authorization", bearer(user(account)))).andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization", bearer(hardware))).andExpect(status().isNotFound());
    }

    private static String serviceToken(String subject, String scope, UUID account) throws Exception {
        Instant now = Instant.now();
        return PerformanceTestAuth.sign(PerformanceTestAuth.AUTH.key(), new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(PerformanceTestAuth.AUTH.key().getKeyID()).type(new JOSEObjectType("svc+jwt")).build(),
                java.util.Map.of("iss", "ltz-auth", "sub", subject, "aud", List.of("performance-service"),
                        "scope", scope, "account_id", account.toString(), "jti", UUID.randomUUID().toString(),
                        "iat", now.getEpochSecond(), "nbf", now.getEpochSecond(), "exp", now.plusSeconds(30).getEpochSecond()));
    }

    @Test
    void jwksOutageFailsClosed() throws Exception {
        UUID account = UUID.randomUUID();
        String fresh = user(account);
        PerformanceTestAuth.setJwksStatus(503);
        try {
            // JWKS önbelleği doluysa mevcut anahtar kullanılabilir; bilinmeyen kid ile kapalı başarısızlık beklenir.
            var unknownKey = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID("bilinmeyen").generate();
            String unknown = PerformanceTestAuth.sign(unknownKey, new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID("bilinmeyen").type(new JOSEObjectType("at+jwt")).build(), java.util.Map.of(
                    "iss", "ltz-auth", "sub", account.toString(), "aud", List.of("ltz-api"),
                    "iat", Instant.now().getEpochSecond(), "nbf", Instant.now().getEpochSecond(),
                    "exp", Instant.now().plusSeconds(60).getEpochSecond()));
            mvc.perform(get(CONSENT).header("Authorization", bearer(unknown))).andExpect(status().isUnauthorized());
        } finally {
            PerformanceTestAuth.setJwksStatus(200);
        }
        mvc.perform(get(CONSENT).header("Authorization", bearer(fresh))).andExpect(status().isOk());
    }
}
