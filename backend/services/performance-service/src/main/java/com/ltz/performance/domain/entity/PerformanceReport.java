package com.ltz.performance.domain.entity;

import com.ltz.performance.domain.enums.GraphicsPreset;
import com.ltz.performance.domain.enums.MeasurementSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/** Tamamlanmış bir oturuma bağlı, elle girilmiş oyun performans raporu (oturum başına en çok bir). */
@Entity
@Table(name = "performance_reports")
@Getter
public class PerformanceReport {
    @Id private UUID id;
    @Column(name = "session_id", nullable = false, updatable = false) private UUID sessionId;
    @Column(name = "user_id", nullable = false, updatable = false) private UUID userId;
    @Column(nullable = false, length = 16) private String resolution;
    @Enumerated(EnumType.STRING) @Column(name = "graphics_preset", nullable = false, length = 16)
    private GraphicsPreset graphicsPreset;
    @Column(name = "avg_fps") private Double avgFps;
    @Column(name = "p1_low_fps") private Double p1LowFps;
    @Column(name = "max_cpu_temp_c") private Double maxCpuTempC;
    @Column(name = "max_gpu_temp_c") private Double maxGpuTempC;
    @Column(name = "avg_cpu_usage_percent") private Double avgCpuUsagePercent;
    @Column(name = "avg_gpu_usage_percent") private Double avgGpuUsagePercent;
    @Column(length = ReportValues.NOTES_MAX) private String notes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false, length = 16) private MeasurementSource source;
    @Version private Long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected PerformanceReport() { }

    public static PerformanceReport createManual(UUID sessionId, UUID userId, ReportValues values, Instant now) {
        var report = new PerformanceReport();
        report.id = UUID.randomUUID();
        report.sessionId = Objects.requireNonNull(sessionId);
        report.userId = Objects.requireNonNull(userId);
        report.source = MeasurementSource.MANUAL;
        report.createdAt = now;
        report.apply(values, now);
        return report;
    }

    /** İdempotent: aynı değerlerle çağrı kaydı değiştirmez; değişiklik olduysa {@code true} döner. */
    public boolean replace(ReportValues values, Instant now) {
        if (values().equals(values)) return false;
        apply(values, now);
        return true;
    }

    public ReportValues values() {
        return new ReportValues(resolution, graphicsPreset, avgFps, p1LowFps, maxCpuTempC, maxGpuTempC,
                avgCpuUsagePercent, avgGpuUsagePercent, notes);
    }

    private void apply(ReportValues values, Instant now) {
        resolution = values.resolution();
        graphicsPreset = values.graphicsPreset();
        avgFps = values.avgFps();
        p1LowFps = values.p1LowFps();
        maxCpuTempC = values.maxCpuTempC();
        maxGpuTempC = values.maxGpuTempC();
        avgCpuUsagePercent = values.avgCpuUsagePercent();
        avgGpuUsagePercent = values.avgGpuUsagePercent();
        notes = values.notes();
        updatedAt = now;
    }
}
