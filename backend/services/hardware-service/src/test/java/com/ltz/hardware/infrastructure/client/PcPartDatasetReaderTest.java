package com.ltz.hardware.infrastructure.client;

import com.ltz.hardware.application.mapper.PcPartMapper;
import com.ltz.hardware.domain.enums.ComponentCategory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PcPartDatasetReaderTest {
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper();

    private PcPartDatasetReader reader(String content, int count) throws IOException {
        Path file = directory.resolve("cpu.json");
        Files.writeString(file, content);
        var pin = new PcPartDatasetReader.FilePin("cpu.json", ComponentCategory.CPU, count, PcPartMapper.digest(Files.readAllBytes(file)));
        return new PcPartDatasetReader(json, new PcPartMapper(json)) {
            @Override public Manifest manifest() { return new Manifest("fixture", "a".repeat(40), List.of(pin)); }
        };
    }

    @Test
    void streamsWhitelistDeduplicatesAndReportsInvalidRowsWithoutSourceContent() throws Exception {
        var reader = reader("""
            [{"name":"AMD Test","core_count":8,"price":{"nested":"DO_NOT_LOG"},"store":[1,2]},
             {"core_count":8,"name":"AMD Test","price":null},
             {"name":"AMD Test","core_count":16}, {"name":"<b>bad</b>"}]
            """, 4);
        var result = reader.read(directory);
        assertThat(result.products()).hasSize(2);
        assertThat(result.files().get("cpu.json").duplicates()).isEqualTo(1);
        assertThat(result.files().get("cpu.json").rejected()).isEqualTo(1);
        assertThat(result.issues().getFirst().row()).isEqualTo(4);
        assertThat(json.writeValueAsString(result)).doesNotContain("DO_NOT_LOG", "price", "store", "<b>");
    }

    @Test
    void verifiesAllChecksumsBeforeReadingAndRejectsMissingFiles() throws Exception {
        var reader = reader("[]", 0);
        Files.writeString(directory.resolve("cpu.json"), "[{}]");
        assertThatThrownBy(() -> reader.read(directory)).isInstanceOf(IOException.class).hasMessageContaining("CHECKSUM");
        Files.delete(directory.resolve("cpu.json"));
        assertThatThrownBy(() -> reader.read(directory)).isInstanceOf(IOException.class).hasMessageContaining("CHECKSUM");
    }

    @Test
    void rejectsWrongCountObjectRootNonObjectRowsAndTrailingPayload() throws Exception {
        var count = reader("[]", 1);
        assertThatThrownBy(() -> count.read(directory)).isInstanceOf(IOException.class).hasMessageContaining("COUNT");
        var object = reader("{}", 0);
        assertThatThrownBy(() -> object.read(directory)).isInstanceOf(IOException.class).hasMessageContaining("ARRAY");
        var row = reader("[1]", 1);
        assertThatThrownBy(() -> row.read(directory)).isInstanceOf(IOException.class).hasMessageContaining("STRUCTURE");
        var trailing = reader("[] {}", 0);
        assertThatThrownBy(() -> trailing.read(directory)).isInstanceOf(IOException.class).hasMessageContaining("COUNT");
    }

    @Test
    void loadsFivePinnedCategoriesAndImmutableRevision() throws Exception {
        var pin = new PcPartDatasetReader(json, new PcPartMapper(json)).manifest();
        assertThat(pin.revision()).isEqualTo("c52a04ca9465c83997ed335f7767b09a2005dd26");
        assertThat(pin.files()).hasSize(5);
        assertThat(pin.files().stream().mapToInt(PcPartDatasetReader.FilePin::count).sum()).isEqualTo(33036);
    }
}
