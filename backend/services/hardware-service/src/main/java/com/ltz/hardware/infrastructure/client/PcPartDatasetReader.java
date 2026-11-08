package com.ltz.hardware.infrastructure.client;

import com.ltz.hardware.application.mapper.PcPartMapper;
import com.ltz.hardware.domain.entity.PcPartProduct;
import com.ltz.hardware.domain.enums.ComponentCategory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.DeserializationFeature;

/** Offline, pinned files only. Unknown fields are skipped before materializing a row. */
@Component
public class PcPartDatasetReader {
    public record FilePin(String file, ComponentCategory category, int count, String sha256) { }
    public record Manifest(String repository, String revision, List<FilePin> files) { }
    public record Issue(ComponentCategory category, int row, String code) { }
    public record FileResult(String sha256, int source, int accepted, int duplicates, int rejected) { }
    public record Dataset(String revision, Instant readAt, Map<String, FileResult> files,
            List<PcPartProduct> products, List<Issue> issues) { }
    private final ObjectMapper json;
    private final PcPartMapper mapper;

    public PcPartDatasetReader(ObjectMapper json, PcPartMapper mapper) { this.json = json; this.mapper = mapper; }

    public Manifest manifest() throws IOException {
        try (var stream = getClass().getResourceAsStream("/catalog/pc-part-dataset.json")) {
            if (stream == null) throw new IOException("DATASET_MANIFEST_MISSING");
            return json.readValue(stream, Manifest.class);
        }
    }

    public Dataset read(Path directory) throws IOException {
        Manifest pin = manifest();
        var products = new ArrayList<PcPartProduct>();
        var issues = new ArrayList<Issue>();
        var files = new TreeMap<String, FileResult>();
        // Verify ALL files before producing any database work.
        for (FilePin file : pin.files()) {
            Path input = directory.resolve(file.file());
            if (!Files.isRegularFile(input) || Files.size(input) > 16_000_000
                    || !PcPartMapper.digest(Files.readAllBytes(input)).equals(file.sha256())) {
                throw new IOException("DATASET_CHECKSUM_MISMATCH: " + file.file());
            }
        }
        for (FilePin file : pin.files()) readFile(directory.resolve(file.file()), file, products, issues, files);
        return new Dataset(pin.revision(), Instant.now(), Map.copyOf(files), List.copyOf(products), List.copyOf(issues));
    }

    private void readFile(Path input, FilePin pin, List<PcPartProduct> products,
            List<Issue> issues, Map<String, FileResult> files) throws IOException {
        int rows = 0;
        int duplicates = 0;
        int rejected = 0;
        int accepted = 0;
        var identities = new HashSet<String>();
        try (var stream = Files.newInputStream(input); var parser = json.createParser(stream)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) throw new IOException("DATASET_ARRAY_REQUIRED");
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (++rows > 100_000 || parser.currentToken() != JsonToken.START_OBJECT) throw new IOException("DATASET_STRUCTURE_INVALID");
                var safe = json.createObjectNode();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String field = parser.currentName();
                    parser.nextToken();
                    if (PcPartMapper.allowed(pin.category(), field)) safe.set(field,
                            json.reader().without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(parser));
                    else parser.skipChildren();
                }
                try {
                    var product = mapper.map(pin.category(), safe);
                    if (identities.add(product.identity())) { products.add(product); accepted++; }
                    else duplicates++;
                } catch (IllegalArgumentException exception) {
                    rejected++;
                    // Never include source payload, exception text or product names in errors.
                    issues.add(new Issue(pin.category(), rows, "INVALID_TECHNICAL_ROW"));
                }
            }
            if (parser.nextToken() != null || rows != pin.count()) throw new IOException("DATASET_COUNT_MISMATCH");
        }
        files.put(pin.file(), new FileResult(pin.sha256(), rows, accepted, duplicates, rejected));
    }
}
