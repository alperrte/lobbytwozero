package com.ltz.hardware.infrastructure.client;

import com.ltz.hardware.api.dto.response.HardwarePerformanceSummaryResponse;
import com.ltz.hardware.api.dto.response.HardwarePerformanceSummaryResponse.Metric;
import com.ltz.hardware.application.port.PerformanceSummaryReader;
import com.ltz.hardware.domain.exception.HardwarePerformanceUnavailableException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Her okumada yeni, hesaba bağlı summary:read token'ı; bozuk/erişilemeyen yanıt asla boş özet sayılmaz. */
@Component
public class PerformanceServiceSummaryReader implements PerformanceSummaryReader {
    private static final Map<String, String> UNITS = Map.of("CPU_TEMP_C", "°C", "GPU_TEMP_C", "°C",
            "CPU_USAGE_PERCENT", "%", "GPU_USAGE_PERCENT", "%", "RAM_USED_MB", "MB", "VRAM_USED_MB", "MB",
            "DISK_READ_MBPS", "MB/s", "DISK_WRITE_MBPS", "MB/s", "FAN_RPM", "RPM", "FPS", "FPS");
    private final HardwareClientProperties properties;
    private final HardwareServiceTokenClient tokens;
    private final HardwareHttpClient http;
    private final ObjectMapper mapper;

    public PerformanceServiceSummaryReader(HardwareClientProperties properties, HardwareServiceTokenClient tokens,
            HardwareHttpClient http, ObjectMapper mapper) {
        this.properties = properties;
        this.tokens = tokens;
        this.http = http;
        this.mapper = mapper;
    }

    @Override
    public HardwarePerformanceSummaryResponse read(UUID accountId, UUID hardwareProfileId) {
        try {
            if (accountId == null || hardwareProfileId == null) throw invalid();
            var uri = properties.performanceServiceUrl().resolve("/internal/v1/accounts/" + accountId
                    + "/performance-summary?hardwareProfileId=" + hardwareProfileId);
            return parse(mapper.readTree(http.get(uri, tokens.summaryToken(accountId))), hardwareProfileId);
        } catch (RuntimeException _) {
            // Dış gövde, token veya hata zinciri loga/ProblemDetail'e taşınmaz.
            throw new HardwarePerformanceUnavailableException();
        }
    }

    private static HardwarePerformanceSummaryResponse parse(JsonNode root, UUID profileId) {
        if (root == null || !profileId.toString().equals(text(root, "hardwareProfileId"))) throw invalid();
        long sessions = count(root, "sessionCount");
        long samples = count(root, "sampleCount");
        JsonNode rows = metricRows(root);
        Instant last = lastMeasured(root);
        var metrics = new ArrayList<Metric>();
        var names = new HashSet<String>();
        long total = 0;
        Instant latest = null;
        for (JsonNode row : rows) {
            Metric metric = metric(row, sessions);
            if (!names.add(metric.metric())) throw invalid();
            total = Math.addExact(total, metric.count());
            if (latest == null || metric.lastAt().isAfter(latest)) latest = metric.lastAt();
            metrics.add(metric);
        }
        if (total != samples || !java.util.Objects.equals(last, latest)) throw invalid();
        return new HardwarePerformanceSummaryResponse(profileId, sessions, samples, last, metrics);
    }

    private static Metric metric(JsonNode row, long sessions) {
        String name = text(row, "metric");
        String unit = text(row, "unit");
        long count = count(row, "count");
        long measuredSessions = count(row, "sessionCount");
        double avg = number(row, "avg");
        double min = number(row, "min");
        double max = number(row, "max");
        Instant first = instant(row, "firstAt");
        Instant last = instant(row, "lastAt");
        if (!unit.equals(UNITS.get(name)) || count == 0 || measuredSessions == 0 || measuredSessions > sessions
                || measuredSessions > count || min < 0 || min > max || avg < min || avg > max || first.isAfter(last)) throw invalid();
        return new Metric(name, unit, count, measuredSessions, avg, min, max, first, last);
    }

    private static JsonNode metricRows(JsonNode root) {
        JsonNode rows = root.get("metrics");
        if (rows == null || !rows.isArray() || rows.size() > UNITS.size()) throw invalid();
        return rows;
    }

    private static Instant lastMeasured(JsonNode root) {
        JsonNode measured = root.get("lastMeasuredAt");
        if (measured == null) throw invalid();
        return measured.isNull() ? null : instant(root, "lastMeasuredAt");
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isString() || value.asString().isBlank() || value.asString().length() > 64) throw invalid();
        return value.asString();
    }

    private static long count(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < 0) throw invalid();
        return value.asLong();
    }

    private static double number(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isNumber() || !Double.isFinite(value.asDouble())) throw invalid();
        return value.asDouble();
    }

    private static Instant instant(JsonNode node, String key) { return Instant.parse(text(node, key)); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid Performance summary"); }
}
