package com.ltz.hardware.api.controller;

import com.ltz.hardware.infrastructure.persistence.PostgresIntegrationSupport;
import com.ltz.hardware.domain.entity.HardwareSyncDigest;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static com.ltz.hardware.api.controller.HardwareProfileControllerTest.actor;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
@AutoConfigureMockMvc
class HardwareScanControllerTest extends PostgresIntegrationSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    UUID owner;
    UUID profile;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM hardware_profiles");
        owner = UUID.randomUUID();
        profile = UUID.randomUUID();
        jdbc.update("INSERT INTO hardware_profiles(id,user_id,name,device_type,created_at,updated_at) VALUES (?,?,'PC','DESKTOP',now(),now())",
                profile, owner);
    }

    String url(UUID id) { return "/api/v1/hardware/profiles/" + id + "/sync/preview"; }
    String component(UUID key) {
        return "{\"componentKey\":\"" + key + "\",\"category\":\"CPU\",\"modelName\":\" CPU Model \",\"quantity\":1}";
    }
    String body(String components, Instant capturedAt) {
        return "{\"processingConsent\":true,\"scan\":{\"schemaVersion\":1,\"capturedAt\":\"" + capturedAt
                + "\",\"components\":[" + components + "]}}";
    }
    String body() { return body(component(UUID.randomUUID()), Instant.now()); }

    @Test
    void normalizesPreviewWithoutPersistingHardwareConsentOrVersion() throws Exception {
        var content = body().replace(" CPU Model ", " ＣＰＵ　Model ").replace("\"quantity\":1",
                "\"brandName\":\" Maker \",\"quantity\":1,\"specifications\":{\"coreCount\":8,\"socket\":\" ＡＭ５ \"}");
        mvc.perform(post(url(profile)).with(actor(owner)).with(csrf()).contentType("application/json").content(content))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.persisted").value(false)).andExpect(jsonPath("$.profileVersion").value(0))
                .andExpect(jsonPath("$.schemaVersion").value(1)).andExpect(jsonPath("$.componentCount").value(1))
                .andExpect(jsonPath("$.components[0].modelName").value("CPU Model"))
                .andExpect(jsonPath("$.components[0].brandName").value("Maker"))
                .andExpect(jsonPath("$.components[0].specifications.socket").value("AM5"))
                .andExpect(jsonPath("$.components[0].source").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profile_components", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM hardware_profiles WHERE id=?", Long.class, profile)).isZero();
        assertThat(jdbc.queryForObject("SELECT source FROM hardware_profiles WHERE id=?", String.class, profile)).isEqualTo("MANUAL");
    }

    @Test
    void acceptsOptionalBrandAndTechnicalFieldsAndThirtyTwoDistinctComponents() throws Exception {
        var entries = IntStream.range(0, 32).mapToObj(_ -> component(UUID.randomUUID()))
                .collect(java.util.stream.Collectors.joining(","));
        mvc.perform(post(url(profile)).with(actor(owner)).with(csrf()).contentType("application/json")
                .content(body(entries, Instant.now())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.componentCount").value(32))
                .andExpect(jsonPath("$.components[0].brandName").isEmpty())
                .andExpect(jsonPath("$.components[0].specifications").isEmpty());
        mvc.perform(post(url(profile)).with(actor(owner)).with(csrf()).contentType("application/json")
                .content(body(entries + "," + component(UUID.randomUUID()), Instant.now())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateKeysAndEmptySnapshots() throws Exception {
        var entry = component(UUID.randomUUID());
        mvc.perform(post(url(profile)).with(actor(owner)).with(csrf()).contentType("application/json")
                .content(body(entry + "," + entry, Instant.now()))).andExpect(status().isBadRequest());
        mvc.perform(post(url(profile)).with(actor(owner)).with(csrf()).contentType("application/json")
                .content(body("", Instant.now()))).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "null"})
    void requiresExplicitProcessingConsent(String consent) throws Exception {
        mvc.perform(post(url(profile)).with(actor(owner)).with(csrf()).contentType("application/json")
                .content(body().replace("\"processingConsent\":true", "\"processingConsent\":" + consent)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_write_limits", Long.class)).isZero();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "\"schemaVersion\":1|\"schemaVersion\":2",
            "\"schemaVersion\":1|\"schemaVersion\":null",
            "\"category\":\"CPU\"|\"category\":\"INVALID\"",
            "\"category\":\"CPU\"|\"category\":null",
            "\"quantity\":1|\"quantity\":0",
            "\"quantity\":1|\"quantity\":129",
            "\"quantity\":1|\"quantity\":null",
            "\"modelName\":\" CPU Model \"|\"modelName\":\" \"",
            "\"modelName\":\" CPU Model \"|\"modelName\":\"＜unsafe＞\"",
            "\"quantity\":1|\"quantity\":1,\"specifications\":{\"clockMhz\":20001}",
            "\"quantity\":1|\"quantity\":1,\"brandName\":\" \"",
            "\"quantity\":1|\"quantity\":1,\"specifications\":{\"socket\":\"＜unsafe＞\"}"})
    void rejectsInvalidScanAndNormalizedTechnicalFields(String original, String replacement) throws Exception {
        var content = body();
        assertThat(content).contains(original);
        mvc.perform(post(url(profile)).with(actor(owner)).with(csrf()).contentType("application/json")
                .content(content.replace(original, replacement)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"userId", "deviceId", "source", "serialNumber", "macAddress", "hostname", "fps"})
    void rejectsMassAssignmentAndUnneededPersonalOrPerformanceData(String field) throws Exception {
        mvc.perform(post(url(profile)).with(actor(owner)).with(csrf()).contentType("application/json")
                .content(body().replace("\"quantity\":1", "\"quantity\":1,\"" + field + "\":\"synthetic\"")))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(longs = {-86410, 310})
    void rejectsOldAndFutureCaptureTime(long offset) throws Exception {
        mvc.perform(post(url(profile)).with(actor(owner)).with(csrf()).contentType("application/json")
                .content(body(component(UUID.randomUUID()), Instant.now().plusSeconds(offset))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enforcesOwnershipAuthenticationCsrfAndBodyLimit() throws Exception {
        mvc.perform(post(url(profile)).with(actor(UUID.randomUUID())).with(csrf()).contentType("application/json").content(body()))
                .andExpect(status().isNotFound());
        mvc.perform(post(url(UUID.randomUUID())).with(actor(owner)).with(csrf()).contentType("application/json").content(body()))
                .andExpect(status().isNotFound());
        mvc.perform(post(url(profile)).with(csrf()).header("X-User-Id", owner).header("X-Role", "ADMIN")
                .header("Authorization", "Bearer invalid").contentType("application/json").content(body()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(url(profile)).with(actor(owner)).contentType("application/json").content(body()))
                .andExpect(status().isForbidden());
        mvc.perform(post(url(profile)).with(user("synthetic").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body())).andExpect(status().isForbidden());
        mvc.perform(post(url(profile)).with(actor(owner)).with(csrf()).contentType("application/json")
                .content(" ".repeat(17000))).andExpect(status().isPayloadTooLarge());
    }

    @Test
    void enforcesUserAndSocketPeerQuotasWithoutTrustingForwardedHeaders() throws Exception {
        jdbc.update("INSERT INTO hardware_write_limits VALUES (?,date_trunc('minute',now()),20)", "sync-user:" + owner);
        mvc.perform(post(url(profile)).with(actor(owner)).with(csrf()).contentType("application/json").content(body()))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
        jdbc.update("DELETE FROM hardware_write_limits");
        jdbc.update("INSERT INTO hardware_write_limits VALUES (?,date_trunc('minute',now()),120)",
                "sync-peer:" + HardwareSyncDigest.hash("127.0.0.1"));
        mvc.perform(post(url(profile)).with(actor(owner)).with(csrf()).header("X-Forwarded-For", "198.51.100.42")
                .contentType("application/json").content(body())).andExpect(status().isTooManyRequests());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_write_limits WHERE bucket_key=?", Long.class,
                "sync-user:" + owner)).isZero();
    }
}
