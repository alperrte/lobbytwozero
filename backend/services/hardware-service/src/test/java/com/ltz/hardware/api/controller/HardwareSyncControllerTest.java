package com.ltz.hardware.api.controller;

import com.ltz.hardware.application.port.*;
import com.ltz.hardware.domain.entity.HardwareSyncDigest;
import com.ltz.hardware.infrastructure.persistence.PostgresIntegrationSupport;
import com.ltz.hardware.infrastructure.persistence.repository.HardwareSyncReceiptRepository;
import com.ltz.hardware.infrastructure.security.UnavailableHardwareSyncDevicePolicy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;
import static com.ltz.hardware.api.controller.HardwareProfileControllerTest.actor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
@AutoConfigureMockMvc
class HardwareSyncControllerTest extends PostgresIntegrationSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired HardwareSyncReceiptRepository receipts;
    @MockitoBean HardwareSyncDevicePolicy policy;
    UUID owner;
    UUID profile;
    UUID device;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM hardware_profiles");
        owner = UUID.randomUUID(); profile = UUID.randomUUID(); device = UUID.randomUUID();
        jdbc.update("INSERT INTO hardware_profiles(id,user_id,name,device_type,created_at,updated_at) VALUES (?,?,'PC','DESKTOP',now(),now())",
                profile, owner);
        when(policy.isActiveOwnedDevice(any(), any())).thenReturn(false);
        when(policy.isActiveOwnedDevice(owner, device)).thenReturn(true);
    }

    static RequestPostProcessor device(UUID owner, UUID device, String... permissions) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedHardwareDevice(owner, device), null,
                Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
    }
    RequestPostProcessor device() { return device(owner, device, HardwareSyncDeviceAccess.PERMISSION); }
    String base() { return "/api/v1/hardware/profiles/" + profile; }
    String consent() { return base() + "/sync-consent"; }
    String snapshots() { return base() + "/sync/snapshots"; }
    long version() { return jdbc.queryForObject("SELECT version FROM hardware_profiles WHERE id=?", Long.class, profile); }
    UUID generation() { return jdbc.queryForObject("SELECT generation FROM hardware_sync_consents WHERE profile_id=?", UUID.class, profile); }
    void grant() throws Exception {
        mvc.perform(put(consent()).with(actor(owner)).with(csrf()).contentType("application/json")
                .content("{\"profileVersion\":" + version() + ",\"deviceId\":\"" + device + "\",\"accepted\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("GRANTED"));
    }
    String component(UUID key, String model) {
        return "{\"componentKey\":\"" + key + "\",\"category\":\"CPU\",\"modelName\":\"" + model + "\",\"quantity\":1}";
    }
    String body(long version, long sequence, String entries, Instant capturedAt) {
        return "{\"profileVersion\":" + version + ",\"consentGeneration\":\"" + generation() + "\",\"sequence\":" + sequence
                + ",\"scan\":{\"schemaVersion\":1,\"capturedAt\":\"" + capturedAt + "\",\"components\":[" + entries + "]}}";
    }
    String body(long version, long sequence, String entries) { return body(version, sequence, entries, Instant.now()); }
    org.springframework.test.web.servlet.ResultActions apply(UUID key, String body) throws Exception {
        return mvc.perform(post(snapshots()).with(device()).with(csrf()).header("Idempotency-Key", key)
                .contentType("application/json").content(body));
    }
    UUID manual(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO hardware_profile_components(id,profile_id,category,model_name,quantity,created_at,updated_at)
            VALUES (?,?,'CPU',?,1,now(),now())
            """, id, profile, name);
        return id;
    }

    @Test
    void grantsOnlyExplicitVerifiedDeviceAndSynchronizesAutomaticSubsetWithoutChangingManualPrivacyOrProfileOrigin() throws Exception {
        mvc.perform(get(consent()).with(actor(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_GRANTED")).andExpect(jsonPath("$.generation").isEmpty());
        var manual = manual("Manual CPU");
        var first = UUID.randomUUID(); var second = UUID.randomUUID(); var third = UUID.randomUUID();
        grant();
        apply(UUID.randomUUID(), body(1, 1, component(first, "Initial") + "," + component(second, "Removed")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.profileVersion").value(2)).andExpect(jsonPath("$.created").value(2));
        apply(UUID.randomUUID(), body(2, 2, component(first, "Updated") + "," + component(third, "New")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profileVersion").value(3))
                .andExpect(jsonPath("$.created").value(1)).andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.removed").value(1));
        assertThat(jdbc.queryForMap("SELECT source,model_name FROM hardware_profile_components WHERE id=?", manual))
                .containsEntry("source", "MANUAL").containsEntry("model_name", "Manual CPU");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profile_components WHERE profile_id=?", Long.class, profile)).isEqualTo(3);
        assertThat(jdbc.queryForMap("SELECT source,visibility FROM hardware_profiles WHERE id=?", profile))
                .containsEntry("source", "MANUAL").containsEntry("visibility", "PRIVATE");
        mvc.perform(get(consent()).with(actor(owner))).andExpect(jsonPath("$.lastSequence").value(2))
                .andExpect(jsonPath("$.lastSyncedAt").isNotEmpty());
        mvc.perform(get(base()).with(actor(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalComponents").value(3));
        mvc.perform(get(base() + "/shared").with(actor(UUID.randomUUID()))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT response_body::text FROM hardware_sync_receipts LIMIT 1", String.class))
                .doesNotContain("Manual CPU", "Initial", owner.toString(), device.toString(), "modelName");
    }

    @Test
    void returnsIdenticalReceiptForNormalizedReorderedRetryEvenAfterUnrelatedOwnerEdit() throws Exception {
        grant();
        var first = component(UUID.randomUUID(), " Model "); var second = component(UUID.randomUUID(), "Other");
        var captured = Instant.now(); var key = UUID.randomUUID();
        var original = body(1, 1, first + "," + second, captured);
        var response = apply(key, original).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(put(base()).with(actor(owner)).with(csrf()).contentType("application/json")
                .content("{\"profileVersion\":2,\"name\":\"Renamed\",\"deviceType\":\"LAPTOP\"}"))
                .andExpect(status().isOk());
        var reordered = body(1, 1, second + "," + first.replace(" Model ", "Model"), captured);
        apply(key, reordered).andExpect(status().isOk()).andExpect(content().json(response));
        assertThat(version()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_sync_receipts", Long.class)).isEqualTo(1);
        apply(key, original.replace("Other", "Changed")).andExpect(status().isConflict());
    }

    @Test
    void rejectsReusedSequenceOldCaptureAndStaleAggregateWithoutChangingReceiptOrState() throws Exception {
        grant();
        var entry = component(UUID.randomUUID(), "CPU"); var captured = Instant.now().minusSeconds(10);
        apply(UUID.randomUUID(), body(1, 1, entry, captured)).andExpect(status().isOk());
        apply(UUID.randomUUID(), body(2, 1, entry)).andExpect(status().isConflict());
        apply(UUID.randomUUID(), body(2, 2, entry, captured.minusSeconds(1))).andExpect(status().isConflict());
        apply(UUID.randomUUID(), body(1, 2, entry)).andExpect(status().isConflict());
        assertThat(version()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT last_sequence FROM hardware_sync_consents", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_sync_receipts", Long.class)).isEqualTo(1);
    }

    @Test
    void revocationBlocksNewAndCachedRequestsAndRegrantCannotReusePreviousGeneration() throws Exception {
        grant(); var oldGeneration = generation(); var key = UUID.randomUUID();
        var content = body(1, 1, component(UUID.randomUUID(), "CPU"));
        apply(key, content).andExpect(status().isOk());
        mvc.perform(delete(consent()).with(actor(owner)).with(csrf()).param("profileVersion", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.profileVersion").value(3));
        apply(key, content).andExpect(status().isConflict());
        grant(); assertThat(generation()).isNotEqualTo(oldGeneration);
        apply(key, content).andExpect(status().isConflict());
        apply(UUID.randomUUID(), body(4, 1, component(UUID.randomUUID(), "New generation"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_sync_consents", Long.class)).isEqualTo(1);
    }

    @Test
    void manualOverrideDetachesSourceAndSubsequentScanPreservesIt() throws Exception {
        grant(); var key = UUID.randomUUID();
        apply(UUID.randomUUID(), body(1, 1, component(key, "Scanned"))).andExpect(status().isOk());
        var id = jdbc.queryForObject("SELECT id FROM hardware_profile_components WHERE profile_id=?", UUID.class, profile);
        mvc.perform(put(base() + "/components/" + id).with(actor(owner)).with(csrf()).contentType("application/json")
                .content("{\"profileVersion\":2,\"category\":\"CPU\",\"modelName\":\"Manual override\",\"quantity\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.component.source").value("MANUAL"));
        assertThat(jdbc.queryForMap("SELECT source,sync_component_key FROM hardware_profile_components WHERE id=?", id))
                .containsEntry("source", "MANUAL").containsEntry("sync_component_key", null);
        apply(UUID.randomUUID(), body(3, 2, component(key, "Rescanned"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT model_name FROM hardware_profile_components WHERE id=?", String.class, id)).isEqualTo("Manual override");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profile_components", Long.class)).isEqualTo(2);
    }

    @Test
    void rejectsBrowserEvenWithSyncPermissionAndDeviceWithoutScopeAndProtectsOwnerControlFromDevices() throws Exception {
        grant(); var content = body(1, 1, component(UUID.randomUUID(), "CPU"));
        var browserWithScope = authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedActor(owner), null,
                List.of(new SimpleGrantedAuthority(HardwareSyncDeviceAccess.PERMISSION))));
        mvc.perform(post(snapshots()).with(browserWithScope).with(csrf()).header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json").content(content)).andExpect(status().isForbidden());
        mvc.perform(post(snapshots()).with(device(owner, device)).with(csrf()).header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json").content(content)).andExpect(status().isForbidden());
        mvc.perform(post(snapshots()).with(csrf()).header("X-User-Id", owner).header("X-Device-Id", device)
                .header("X-Role", "ADMIN").header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json").content(content)).andExpect(status().isUnauthorized());
        mvc.perform(post(snapshots()).with(device()).header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json").content(content)).andExpect(status().isForbidden());
        mvc.perform(get(consent()).with(device())).andExpect(status().isForbidden());
        mvc.perform(get(consent()).with(actor(UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    @Test
    void rechecksDevicePolicyBeforeReturningCachedReceiptAndCannotGrantUnknownDevice() throws Exception {
        grant(); var key = UUID.randomUUID(); var content = body(1, 1, component(UUID.randomUUID(), "CPU"));
        apply(key, content).andExpect(status().isOk());
        when(policy.isActiveOwnedDevice(owner, device)).thenReturn(false);
        apply(key, content).andExpect(status().isNotFound());
        when(policy.isActiveOwnedDevice(owner, device)).thenThrow(new IllegalStateException("synthetic unavailable"));
        apply(key, content).andExpect(status().isNotFound());
        assertThat(version()).isEqualTo(2);
        mvc.perform(put(consent()).with(actor(owner)).with(csrf()).contentType("application/json")
                .content("{\"profileVersion\":2,\"deviceId\":\"" + UUID.randomUUID() + "\",\"accepted\":true}"))
                .andExpect(status().isNotFound());
        var unavailable = new UnavailableHardwareSyncDevicePolicy();
        assertThat(unavailable.isActiveOwnedDevice(owner, device)).isFalse();
        assertThatThrownBy(() -> HardwareSyncDevicePolicy.requireActiveOwnedDevice(unavailable, owner, device))
                .isInstanceOf(com.ltz.hardware.domain.exception.HardwareNotFoundException.class);
    }

    @Test
    void enforcesOwnerAndBoundDeviceAndRequiresExistingGrant() throws Exception {
        grant(); var content = body(1, 1, component(UUID.randomUUID(), "CPU"));
        var other = UUID.randomUUID();
        when(policy.isActiveOwnedDevice(owner, other)).thenReturn(true);
        mvc.perform(post(snapshots()).with(device(owner, other, HardwareSyncDeviceAccess.PERMISSION)).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json").content(content))
                .andExpect(status().isNotFound());
        var outsider = UUID.randomUUID();
        when(policy.isActiveOwnedDevice(outsider, device)).thenReturn(true);
        mvc.perform(post(snapshots()).with(device(outsider, device, HardwareSyncDeviceAccess.PERMISSION)).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json").content(content))
                .andExpect(status().isNotFound());
        jdbc.update("DELETE FROM hardware_sync_consents WHERE profile_id=?", profile);
        apply(UUID.randomUUID(), content).andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "null"})
    void refusesGrantWithoutExplicitAcceptance(String accepted) throws Exception {
        mvc.perform(put(consent()).with(actor(owner)).with(csrf()).contentType("application/json")
                .content("{\"profileVersion\":0,\"deviceId\":\"" + device + "\",\"accepted\":" + accepted + "}"))
                .andExpect(status().isBadRequest());
        assertThat(version()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_sync_consents", Long.class)).isZero();
    }

    @Test
    void requiresRevocationBeforeNewGrantAndRejectsStaleConsentMutations() throws Exception {
        grant();
        mvc.perform(put(consent()).with(actor(owner)).with(csrf()).contentType("application/json")
                .content("{\"profileVersion\":1,\"deviceId\":\"" + device + "\",\"accepted\":true}"))
                .andExpect(status().isConflict());
        mvc.perform(delete(consent()).with(actor(owner)).with(csrf()).param("profileVersion", "0"))
                .andExpect(status().isConflict());
        mvc.perform(delete(consent()).with(actor(owner)).with(csrf()).param("profileVersion", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profileVersion").value(2));
        mvc.perform(delete(consent()).with(actor(owner)).with(csrf()).param("profileVersion", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profileVersion").value(2));
    }

    @Test
    void missingAndMalformedIdempotencyHeadersAndUnknownFieldsAreRejected() throws Exception {
        grant(); var content = body(1, 1, component(UUID.randomUUID(), "CPU"));
        mvc.perform(post(snapshots()).with(device()).with(csrf()).contentType("application/json").content(content))
                .andExpect(status().isBadRequest());
        mvc.perform(post(snapshots()).with(device()).with(csrf()).header("Idempotency-Key", "invalid")
                .contentType("application/json").content(content)).andExpect(status().isBadRequest());
        apply(UUID.randomUUID(), content.replace("\"sequence\":1", "\"sequence\":0")).andExpect(status().isBadRequest());
        apply(UUID.randomUUID(), content.replace("\"sequence\":1", "\"sequence\":1,\"source\":\"MANUAL\""))
                .andExpect(status().isBadRequest());
        apply(UUID.randomUUID(), content.replace("\"schemaVersion\":1", "\"schemaVersion\":2")).andExpect(status().isBadRequest());
        mvc.perform(post(snapshots()).with(device()).with(csrf()).header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json").content(" ".repeat(17000))).andExpect(status().isPayloadTooLarge());
        assertThat(version()).isEqualTo(1);
    }

    @Test
    void fullReceiptBudgetRollsBackFlushedHardwareVersionSequenceAndRateCounters() throws Exception {
        grant();
        jdbc.update("""
            INSERT INTO hardware_sync_receipts(id,profile_id,idempotency_key,generation,sequence,request_hash,response_body,created_at,expires_at)
            SELECT md5(n::text)::uuid,?,md5(n::text)::uuid,?,n,repeat('0',64),'{}'::jsonb,now(),now()+interval '24 hours'
            FROM generate_series(1,500) AS n
            """, profile, generation());
        apply(UUID.randomUUID(), body(1, 501, component(UUID.randomUUID(), "Not saved")))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("SYNC_RECEIPT_LIMIT_REACHED"));
        assertThat(version()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_sequence FROM hardware_sync_consents", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profile_components", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_write_limits WHERE bucket_key LIKE 'sync-apply-%'", Long.class)).isZero();
        jdbc.update("UPDATE hardware_sync_receipts SET created_at=now()-interval '25 hours',expires_at=now()-interval '1 hour'");
        receipts.clearExpired();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_sync_receipts", Long.class)).isZero();
        apply(UUID.randomUUID(), body(1, 1, component(UUID.randomUUID(), "Saved"))).andExpect(status().isOk());
        jdbc.update("UPDATE hardware_sync_receipts SET created_at=now()-interval '25 hours',expires_at=now()-interval '1 hour'");
        receipts.clearExpired();
        apply(UUID.randomUUID(), body(2, 1, component(UUID.randomUUID(), "Old sequence"))).andExpect(status().isConflict());
        assertThat(version()).isEqualTo(2);
    }

    @Test
    void oversizedCombinedManualAndAutomaticSetIsAtomic() throws Exception {
        grant();
        jdbc.update("""
            INSERT INTO hardware_profile_components(id,profile_id,category,model_name,quantity,created_at,updated_at)
            SELECT md5(n::text)::uuid,?,'CPU','Manual',1,now(),now() FROM generate_series(1,200) AS n
            """, profile);
        apply(UUID.randomUUID(), body(1, 1, component(UUID.randomUUID(), "Overflow"))).andExpect(status().isBadRequest());
        assertThat(version()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profile_components", Long.class)).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT last_sequence FROM hardware_sync_consents", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_sync_receipts", Long.class)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"sync-apply-user:,30", "sync-device:,10", "sync-peer:,120"})
    void enforcesActorDeviceAndHashedSocketPeerLimits(String prefix, int hits) throws Exception {
        grant();
        String identity = prefix.equals("sync-apply-user:") ? owner.toString() : device.toString();
        if (prefix.equals("sync-device:")) identity = owner + ":" + device;
        if (prefix.equals("sync-peer:")) identity = HardwareSyncDigest.hash("127.0.0.1");
        jdbc.update("DELETE FROM hardware_write_limits");
        jdbc.update("INSERT INTO hardware_write_limits VALUES (?,date_trunc('minute',now()),?)", prefix + identity, hits);
        apply(UUID.randomUUID(), body(1, 1, component(UUID.randomUUID(), "Limited")))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        assertThat(version()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_sync_receipts", Long.class)).isZero();
    }

    @Test
    void databaseConstraintsAndProfileCascadePreserveOtherProfiles() throws Exception {
        grant(); var key = UUID.randomUUID();
        apply(UUID.randomUUID(), body(1, 1, component(key, "CPU"))).andExpect(status().isOk());
        assertThatThrownBy(() -> jdbc.update("UPDATE hardware_profile_components SET source='MANUAL' WHERE profile_id=?", profile))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE hardware_profile_components SET sync_component_key=NULL WHERE profile_id=?", profile))
                .isInstanceOf(DataIntegrityViolationException.class);
        var duplicateId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO hardware_profile_components(id,profile_id,category,model_name,quantity,source,sync_component_key,created_at,updated_at)
                SELECT ?,profile_id,category,model_name,quantity,source,sync_component_key,now(),now()
                FROM hardware_profile_components WHERE profile_id=? LIMIT 1
                """, duplicateId, profile)).isInstanceOf(DataIntegrityViolationException.class);
        var other = UUID.randomUUID();
        jdbc.update("INSERT INTO hardware_profiles(id,user_id,name,device_type,created_at,updated_at) VALUES (?,?,'Other','DESKTOP',now(),now())",
                other, UUID.randomUUID());
        mvc.perform(delete(base()).with(actor(owner)).with(csrf()).param("profileVersion", "2")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_sync_consents", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_sync_receipts", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profile_components", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profiles WHERE id=?", Long.class, other)).isEqualTo(1);
    }

    @Test
    void concurrentIdenticalRetriesApplyExactlyOnce() throws Exception {
        grant(); var key = UUID.randomUUID(); var content = body(1, 1, component(UUID.randomUUID(), "CPU"));
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<Integer> call = () -> { barrier.await(10, TimeUnit.SECONDS); return apply(key, content).andReturn().getResponse().getStatus(); };
            var first = pool.submit(call); var second = pool.submit(call);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactly(200, 200);
        }
        assertThat(version()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_sync_receipts", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profile_components", Long.class)).isEqualTo(1);
    }

    @Test
    void ownerEditAndApplyRaceShareAggregateVersion() throws Exception {
        grant(); var content = body(1, 1, component(UUID.randomUUID(), "CPU")); var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return apply(UUID.randomUUID(), content).andReturn().getResponse().getStatus(); });
            var second = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return mvc.perform(put(base()).with(actor(owner)).with(csrf()).contentType("application/json")
                        .content("{\"profileVersion\":1,\"name\":\"Owner edit\",\"deviceType\":\"DESKTOP\"}"))
                        .andReturn().getResponse().getStatus();
            });
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(version()).isEqualTo(2);
    }

    @Test
    void revokeAndApplyRaceHasOneWinnerAndRevokedStateCannotContainNewSequence() throws Exception {
        grant(); var content = body(1, 1, component(UUID.randomUUID(), "CPU")); var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return apply(UUID.randomUUID(), content).andReturn().getResponse().getStatus(); });
            var second = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return mvc.perform(delete(consent()).with(actor(owner)).with(csrf()).param("profileVersion", "1"))
                        .andReturn().getResponse().getStatus();
            });
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(version()).isEqualTo(2);
        var state = jdbc.queryForMap("SELECT status,last_sequence FROM hardware_sync_consents");
        if (state.get("status").equals("REVOKED")) assertThat(state).containsEntry("last_sequence", 0L);
        else assertThat(state).containsEntry("status", "GRANTED").containsEntry("last_sequence", 1L);
    }
}
