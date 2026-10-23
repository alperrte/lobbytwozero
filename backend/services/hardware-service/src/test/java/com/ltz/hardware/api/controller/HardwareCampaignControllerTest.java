package com.ltz.hardware.api.controller;

import com.ltz.hardware.infrastructure.persistence.PostgresIntegrationSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static com.ltz.hardware.api.controller.HardwareProfileControllerTest.actor;
import static com.ltz.hardware.api.controller.HardwareCatalogControllerTest.writer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
@AutoConfigureMockMvc
class HardwareCampaignControllerTest extends PostgresIntegrationSupport {
    static final String PATH = "/api/v1/hardware/campaigns";
    final UUID manager = UUID.randomUUID();
    final UUID product = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM hardware_campaigns");
        jdbc.update("DELETE FROM hardware_profiles");
        jdbc.update("DELETE FROM hardware_product_brands");
        jdbc.update("DELETE FROM hardware_products");
        jdbc.update("DELETE FROM hardware_brands");
        jdbc.update("INSERT INTO hardware_products(id,category,model,normalized_model) VALUES (?,'GPU','GPU','gpu')", product);
    }

    Map<String,Object> payload() {
        var result = new HashMap<String,Object>();
        result.put("productId", product);
        result.put("title", "Synthetic hardware discount");
        result.put("sourceName", "Synthetic merchant");
        result.put("sourceUrl", "https://merchant.example/deal");
        result.put("originalPrice", new BigDecimal("100.00"));
        result.put("salePrice", new BigDecimal("75.00"));
        result.put("currency", "TRY");
        result.put("startsAt", Instant.now().minusSeconds(3600));
        result.put("endsAt", Instant.now().plusSeconds(3600));
        result.put("observedAt", Instant.now().minusSeconds(60));
        result.put("reason", "Synthetic source record");
        return result;
    }

    String json(Object value) { return mapper.writeValueAsString(value); }

    UUID create(Map<String,Object> body) throws Exception {
        String response = mvc.perform(post(PATH).with(writer(manager)).with(csrf()).contentType("application/json")
                .content(json(body))).andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(response).get("id").asText());
    }

    String archive(long version) { return json(Map.of("version", version, "reason", "Synthetic archive")); }

    @Test
    void returnsRecordedSourcePriceAndObservationWithCalculatedDiscount() throws Exception {
        UUID id = create(payload());
        mvc.perform(get(PATH + "/" + id).with(actor(UUID.randomUUID())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.productId").value(product.toString()))
                .andExpect(jsonPath("$.sourceUrl").value("https://merchant.example/deal"))
                .andExpect(jsonPath("$.observedAt").exists()).andExpect(jsonPath("$.currency").value("TRY"))
                .andExpect(jsonPath("$.discountPercent").value(25.0))
                .andExpect(jsonPath("$.originalPrice").value(100.0)).andExpect(jsonPath("$.salePrice").value(75.0))
                .andExpect(jsonPath("$.actorId").doesNotExist()).andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store, private"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_catalog_audit WHERE target_id=? AND action='CREATE_CAMPAIGN'",
                Long.class, id)).isEqualTo(1);
    }

    @Test
    void listFiltersValidityProductAndArchiveAndUsesConsistentTotals() throws Exception {
        UUID active = create(payload());
        var future = payload();
        future.put("startsAt", Instant.now().plusSeconds(7200));
        future.put("endsAt", Instant.now().plusSeconds(10800));
        UUID scheduled = create(future);
        var expired = payload();
        expired.put("startsAt", Instant.now().minusSeconds(7200));
        expired.put("endsAt", Instant.now().minusSeconds(3600));
        UUID ended = create(expired);
        UUID archived = create(payload());
        mvc.perform(delete(PATH + "/" + archived).with(writer(manager)).with(csrf())
                .contentType("application/json").content(archive(0))).andExpect(status().isNoContent());
        mvc.perform(get(PATH).with(actor(manager)).param("productId", product.toString()).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(active.toString()));
        for (UUID id : List.of(scheduled, ended, archived)) {
            mvc.perform(get(PATH + "/" + id).with(actor(manager))).andExpect(status().isNotFound());
        }
        mvc.perform(get(PATH).with(actor(manager)).param("productId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        jdbc.update("UPDATE hardware_products SET status='ARCHIVED' WHERE id=?", product);
        mvc.perform(get(PATH).with(actor(manager))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(PATH + "/" + active).with(actor(manager))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://merchant.example/deal", "javascript:alert(1)", "//merchant.example/deal",
            "https://user:pass@merchant.example/deal", "https://merchant.example/deal#fragment", "not a URL", "https:///deal"})
    void rejectsUnsafeSourcesWithoutWritingCampaignOrAudit(String url) throws Exception {
        var body = payload();
        body.put("sourceUrl", url);
        mvc.perform(post(PATH).with(writer(manager)).with(csrf()).contentType("application/json").content(json(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_campaigns", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_catalog_audit WHERE actor_id=?",
                Long.class, manager)).isZero();
    }

    @Test
    void rejectsInvalidPriceCurrencyDatesAndMassAssignment() throws Exception {
        var invalids = List.of(Map.entry("salePrice", (Object)new BigDecimal("100.00")),
                Map.entry("originalPrice", (Object)BigDecimal.ZERO), Map.entry("salePrice", (Object)new BigDecimal("-1")),
                Map.entry("salePrice", (Object)new BigDecimal("1.001")), Map.entry("currency", (Object)"ZZZ"),
                Map.entry("currency", (Object)"try"), Map.entry("currency", (Object)"XXX"),
                Map.entry("sourceUrl", (Object)("https://merchant.example/" + "ş".repeat(500))),
                Map.entry("endsAt", (Object)Instant.now().minusSeconds(7200)),
                Map.entry("observedAt", (Object)Instant.now().plusSeconds(3600)),
                Map.entry("userId", (Object)manager), Map.entry("status", (Object)"ACTIVE"),
                Map.entry("reason", (Object)" "), Map.entry("title", (Object)"<script>"),
                Map.entry("originalPrice", (Object)new BigDecimal("10000000000.00")));
        for (var invalid : invalids) {
            var body = payload();
            body.put(invalid.getKey(), invalid.getValue());
            mvc.perform(post(PATH).with(writer(manager)).with(csrf()).contentType("application/json").content(json(body)))
                    .andExpect(status().isBadRequest());
        }
        var missing = payload();
        missing.remove("observedAt");
        mvc.perform(post(PATH).with(writer(manager)).with(csrf()).contentType("application/json").content(json(missing)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_campaigns", Long.class)).isZero();
    }

    @Test
    void rejectsMissingOrArchivedProductsAndInvalidCreateUpdateModes() throws Exception {
        var missing = payload();
        missing.put("productId", UUID.randomUUID());
        mvc.perform(post(PATH).with(writer(manager)).with(csrf()).contentType("application/json").content(json(missing)))
                .andExpect(status().isNotFound());
        var invalidCreate = payload();
        invalidCreate.put("version", 0);
        mvc.perform(post(PATH).with(writer(manager)).with(csrf()).contentType("application/json").content(json(invalidCreate)))
                .andExpect(status().isBadRequest());
        invalidCreate.remove("version");
        invalidCreate.remove("productId");
        mvc.perform(post(PATH).with(writer(manager)).with(csrf()).contentType("application/json").content(json(invalidCreate)))
                .andExpect(status().isBadRequest());
        UUID id = create(payload());
        mvc.perform(put(PATH + "/" + id).with(writer(manager)).with(csrf()).contentType("application/json").content(json(payload())))
                .andExpect(status().isBadRequest());
        jdbc.update("UPDATE hardware_products SET status='ARCHIVED' WHERE id=?", product);
        mvc.perform(post(PATH).with(writer(manager)).with(csrf()).contentType("application/json").content(json(payload())))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatesWithVersionAndArchivesWithoutDeletingEvidence() throws Exception {
        UUID id = create(payload());
        var update = payload();
        update.remove("productId");
        update.put("version", 0);
        update.put("salePrice", new BigDecimal("60.00"));
        mvc.perform(put(PATH + "/" + id).with(writer(manager)).with(csrf()).contentType("application/json").content(json(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.discountPercent").value(40.0))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(put(PATH + "/" + id).with(writer(manager)).with(csrf()).contentType("application/json").content(json(update)))
                .andExpect(status().isConflict());
        mvc.perform(delete(PATH + "/" + id).with(writer(manager)).with(csrf())
                .contentType("application/json").content(archive(0))).andExpect(status().isConflict());
        mvc.perform(delete(PATH + "/" + id).with(writer(manager)).with(csrf())
                .contentType("application/json").content(archive(1))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_campaigns WHERE id=?", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_catalog_audit WHERE target_id=?", Long.class, id)).isEqualTo(3);
        update.put("version", 2);
        mvc.perform(put(PATH + "/" + id).with(writer(manager)).with(csrf()).contentType("application/json").content(json(update)))
                .andExpect(status().isConflict());
        mvc.perform(delete(PATH + "/" + UUID.randomUUID()).with(writer(manager)).with(csrf())
                .contentType("application/json").content(archive(0))).andExpect(status().isNotFound());
        mvc.perform(put(PATH + "/" + UUID.randomUUID()).with(writer(manager)).with(csrf())
                .contentType("application/json").content(json(update))).andExpect(status().isNotFound());
    }

    @Test
    void enforcesAuthenticationWritePermissionCsrfAndInputBounds() throws Exception {
        UUID id = create(payload());
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).with(user("untrusted").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post(PATH).with(actor(manager)).with(csrf()).contentType("application/json").content(json(payload())))
                .andExpect(status().isForbidden());
        mvc.perform(post(PATH).with(writer(manager)).contentType("application/json").content(json(payload())))
                .andExpect(status().isForbidden());
        for (var method : List.of(put(PATH + "/" + id), delete(PATH + "/" + id))) {
            mvc.perform(method.with(actor(manager)).with(csrf()).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
        for (String size : List.of("0", "101")) {
            mvc.perform(get(PATH).with(actor(manager)).param("size", size)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(PATH).with(actor(manager)).param("page", "10001")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).with(actor(manager)).param("productId", "bad")).andExpect(status().isBadRequest());
        mvc.perform(delete(PATH + "/" + id).with(writer(manager)).with(csrf())
                .contentType("application/json").content("{\"reason\":\"" + "x".repeat(17000) + "\"}"))
                .andExpect(status().is(413));
    }

    @Test
    void concurrentCampaignUpdatesHaveOneWinnerAndNoDuplicateAudit() throws Exception {
        UUID id = create(payload());
        var update = payload();
        update.remove("productId");
        update.put("version", 0);
        String body = json(update);
        var latch = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                latch.await();
                return mvc.perform(put(PATH + "/" + id).with(writer(manager)).with(csrf()).contentType("application/json")
                        .content(body)).andReturn().getResponse().getStatus();
            })).toList();
            latch.countDown();
            assertThat(List.of(tasks.get(0).get(20, TimeUnit.SECONDS), tasks.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200,409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_catalog_audit WHERE target_id=?", Long.class, id)).isEqualTo(2);
    }

    @Test
    void databaseChecksRejectInvalidDiscountAndPeriod() throws Exception {
        UUID id = create(payload());
        assertThatThrownBy(() -> jdbc.update("UPDATE hardware_campaigns SET sale_price=original_price WHERE id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE hardware_campaigns SET ends_at=starts_at WHERE id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM hardware_products WHERE id=?", product))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
