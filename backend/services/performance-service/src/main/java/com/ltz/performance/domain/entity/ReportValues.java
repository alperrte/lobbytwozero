package com.ltz.performance.domain.entity;

import com.ltz.performance.domain.enums.GraphicsPreset;
import com.ltz.performance.domain.exception.PerformanceUnprocessableException;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Kullanıcının elle girdiği rapor değerleri. Girilmeyen ölçüm {@code null} kalır; sistem hiçbir değeri
 * kendiliğinden doldurmaz. Aralık doğrulaması DTO ve veritabanı CHECK'lerindedir; burada alanlar arası
 * kurallar uygulanır.
 */
public record ReportValues(String resolution, GraphicsPreset graphicsPreset, Double avgFps, Double p1LowFps,
        Double maxCpuTempC, Double maxGpuTempC, Double avgCpuUsagePercent, Double avgGpuUsagePercent, String notes) {
    public static final int NOTES_MAX = 500;

    public ReportValues {
        Objects.requireNonNull(resolution);
        Objects.requireNonNull(graphicsPreset);
        if (Stream.of(avgFps, p1LowFps, maxCpuTempC, maxGpuTempC, avgCpuUsagePercent, avgGpuUsagePercent)
                .allMatch(Objects::isNull)) {
            throw new PerformanceUnprocessableException(PerformanceUnprocessableException.REPORT_EMPTY);
        }
        if (avgFps != null && p1LowFps != null && p1LowFps > avgFps) {
            throw new PerformanceUnprocessableException(PerformanceUnprocessableException.REPORT_INCONSISTENT);
        }
        notes = normalizeNotes(notes);
    }

    static String normalizeNotes(String raw) {
        if (raw == null) return null;
        var cleaned = raw.replaceAll("\\p{Cntrl}", " ").strip();
        if (cleaned.isEmpty()) return null;
        return cleaned.codePointCount(0, cleaned.length()) <= NOTES_MAX ? cleaned
                : cleaned.substring(0, cleaned.offsetByCodePoints(0, NOTES_MAX));
    }
}
