package com.ltz.hardware.api.controller;

import com.ltz.hardware.application.mapper.PcPartMapper;
import com.ltz.hardware.application.service.PcPartImportService;
import com.ltz.hardware.domain.entity.PcPartProduct;
import com.ltz.hardware.domain.enums.ComponentCategory;
import com.ltz.hardware.infrastructure.client.PcPartDatasetReader.Dataset;
import com.ltz.hardware.infrastructure.persistence.PostgresIntegrationSupport;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static com.ltz.hardware.api.controller.HardwareProfileControllerTest.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
@AutoConfigureMockMvc
class PcPartImportIntegrationTest extends PostgresIntegrationSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired PcPartImportService importer;
    @Autowired PcPartMapper mapper;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM hardware_campaigns");
        jdbc.update("DELETE FROM hardware_profiles");
        jdbc.update("DELETE FROM hardware_product_brands");
        jdbc.update("DELETE FROM hardware_products");
        jdbc.update("DELETE FROM hardware_brands");
        jdbc.update("DELETE FROM hardware_dataset_import_runs");
    }

    private PcPartProduct row(ComponentCategory category, String data) { return mapper.map(category, json.readTree(data)); }
    private Dataset dataset(List<PcPartProduct> products) { return new Dataset("a".repeat(40), Instant.now(), Map.of(), products, List.of()); }
    private List<PcPartProduct> products() {
        return List.of(row(ComponentCategory.CPU, "{\"name\":\"AMD Test CPU\",\"core_clock\":4.2,\"core_count\":8}"),
                row(ComponentCategory.MOTHERBOARD, "{\"name\":\"Asus Test Board\",\"socket\":\"AM5\",\"memory_slots\":4}"),
                row(ComponentCategory.RAM, "{\"name\":\"Corsair Test RAM\",\"modules\":[2,16],\"speed\":[5,6000]}"),
                row(ComponentCategory.GPU, "{\"name\":\"MSI GeForce Test\",\"chipset\":\"GeForce Test\",\"memory\":12,\"core_clock\":1320,\"boost_clock\":1777,\"length\":235}"),
                row(ComponentCategory.STORAGE, "{\"name\":\"Samsung 990 Pro\",\"capacity\":1000,\"type\":\"SSD\"}"),
                row(ComponentCategory.STORAGE, "{\"name\":\"Samsung 990 Pro\",\"capacity\":2000,\"type\":\"SSD\"}"));
    }

    @Test
    void dryRunNeverWritesAndSecondImportKeepsUuidsVersionsReviewsAndManualProducts() {
        UUID manual = UUID.randomUUID();
        jdbc.update("INSERT INTO hardware_products(id,category,model,normalized_model) VALUES (?,'CPU','Test CPU','test cpu')", manual);
        var input = dataset(products());
        assertThat(importer.run(input, 2, true).categories().get("STORAGE").added()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_products", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_product_sources", Integer.class)).isZero();
        assertThat(importer.run(input, 2, false).categories().get("CPU").added()).isEqualTo(1);
        var ids = jdbc.queryForList("SELECT id,version FROM hardware_products ORDER BY id");
        UUID product = jdbc.queryForObject("SELECT product_id FROM hardware_product_sources WHERE category='CPU'", UUID.class);
        UUID review = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO hardware_reviews(id,product_id,user_id,body,body_hash,rating,status,created_at,updated_at)
            VALUES (?,?,?,'Existing experience',?,5,'VISIBLE',now(),now())
            """, review, product, UUID.randomUUID(), "b".repeat(64));
        UUID profile = UUID.randomUUID();
        jdbc.update("INSERT INTO hardware_profiles(id,user_id,name,device_type,created_at,updated_at) VALUES (?,?,'Existing PC','DESKTOP',now(),now())",
                profile, UUID.randomUUID());
        jdbc.update("""
            INSERT INTO hardware_profile_components(id,profile_id,category,product_id,model_name,quantity,source,created_at,updated_at)
            VALUES (?,?,'CPU',?,'Test CPU',1,'MANUAL',now(),now())
            """, UUID.randomUUID(), profile, product);
        var result = importer.run(input, 2, false);
        assertThat(result.categories().get("CPU").unchanged()).isEqualTo(1);
        assertThat(result.categories().get("GPU").unchanged()).isEqualTo(1);
        assertThat(result.categories().get("STORAGE").unchanged()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT id,version FROM hardware_products ORDER BY id")).isEqualTo(ids);
        assertThat(jdbc.queryForObject("SELECT product_id FROM hardware_reviews WHERE id=?", UUID.class, review)).isEqualTo(product);
        assertThat(jdbc.queryForObject("SELECT product_id FROM hardware_profile_components WHERE profile_id=?", UUID.class, profile)).isEqualTo(product);
        assertThat(jdbc.queryForObject("SELECT catalog_variant FROM hardware_products WHERE id=?", String.class, manual)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_dataset_import_runs", Integer.class)).isEqualTo(2);
    }

    @Test
    void authenticatedListDetailAndFiveCategoryFiltersExposeTechnicalSourceOnly() throws Exception {
        importer.run(dataset(products()), 50, false);
        for (var category : List.of("CPU", "GPU", "MOTHERBOARD", "RAM", "STORAGE")) {
            mvc.perform(get(HardwareCatalogSearchTest.PATH).with(actor(UUID.randomUUID())).param("category", category).param("discovery", "true"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].category").value(category))
                    .andExpect(jsonPath("$.items[0].source.provider").value("PC_PART_DATASET"))
                    .andExpect(jsonPath("$.items[0].source.typedSpecifications").isMap())
                    .andExpect(jsonPath("$.items[0].source.typedSpecifications.price").doesNotExist());
        }
        UUID product = jdbc.queryForObject("SELECT product_id FROM hardware_product_sources WHERE category='CPU'", UUID.class);
        mvc.perform(get(HardwareCatalogSearchTest.PATH + "/" + product).with(actor(UUID.randomUUID())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.specifications.clockMhz").value(4200))
                .andExpect(jsonPath("$.source.provider").value("PC_PART_DATASET"));
        mvc.perform(get(HardwareCatalogSearchTest.PATH).with(actor(UUID.randomUUID()))
                .param("category", "STORAGE").param("minCapacityGb", "2000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(HardwareCatalogSearchTest.PATH)).andExpect(status().isUnauthorized());
        UUID gpu = jdbc.queryForObject("SELECT product_id FROM hardware_product_sources WHERE category='GPU'", UUID.class);
        mvc.perform(get(HardwareCatalogSearchTest.PATH + "/" + gpu).with(actor(UUID.randomUUID())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.category").value("GPU"))
                .andExpect(jsonPath("$.specifications.capacityGb").value(12))
                .andExpect(jsonPath("$.source.typedSpecifications.boostClockMhz.number").value(1777))
                .andExpect(jsonPath("$.source.typedSpecifications.lengthMm.number").value(235));
    }

    @Test
    void failedBatchRollsBackAllItsProductsAndLaterBatchesContinue() {
        jdbc.execute("""
            CREATE FUNCTION reject_pc_part_fixture() RETURNS trigger LANGUAGE plpgsql AS $$
            BEGIN IF NEW.model='Reject me' THEN RAISE EXCEPTION 'fixture'; END IF; RETURN NEW; END; $$
            """);
        jdbc.execute("CREATE TRIGGER reject_pc_part_fixture BEFORE INSERT ON hardware_products FOR EACH ROW EXECUTE FUNCTION reject_pc_part_fixture()");
        try {
            var rows = List.of(products().getFirst(), row(ComponentCategory.CPU, "{\"name\":\"AMD Reject me\"}"), products().getLast());
            var result = importer.run(dataset(rows), 2, false);
            assertThat(result.categories().get("CPU").failed()).isEqualTo(2);
            assertThat(result.categories().get("STORAGE").added()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_products", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_product_sources", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM hardware_dataset_import_runs", String.class)).isEqualTo("PARTIAL");
        } finally {
            jdbc.execute("DROP TRIGGER reject_pc_part_fixture ON hardware_products");
            jdbc.execute("DROP FUNCTION reject_pc_part_fixture()");
        }
    }

    @Test
    void updatesOnlyMappedImportedProductAndNeverRevivesArchivedProduct() {
        var input = dataset(List.of(products().getFirst()));
        importer.run(input, 1, false);
        UUID product = jdbc.queryForObject("SELECT product_id FROM hardware_product_sources", UUID.class);
        jdbc.update("UPDATE hardware_product_sources SET content_hash='old'");
        assertThat(importer.run(input, 1, false).categories().get("CPU").updated()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT id FROM hardware_products", UUID.class)).isEqualTo(product);
        jdbc.update("UPDATE hardware_products SET status='ARCHIVED' WHERE id=?", product);
        assertThat(importer.run(input, 1, false).categories().get("CPU").skipped()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM hardware_products WHERE id=?", String.class, product)).isEqualTo("ARCHIVED");
        assertThatThrownBy(() -> importer.run(input, 101, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
