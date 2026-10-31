package com.ltz.performance.api.dto.request;

import com.ltz.performance.domain.entity.ReportValues;
import com.ltz.performance.domain.enums.GraphicsPreset;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Elle girilmiş rapor. Ölçümler isteğe bağlıdır ve girilmeyen değer doldurulmaz; en az biri gereklidir.
 * Aralıklar veritabanı CHECK kısıtlarıyla aynıdır.
 */
public record ReportRequest(
        @NotNull @Pattern(regexp = "^\\d{3,5}x\\d{3,5}$") String resolution,
        @NotNull GraphicsPreset graphicsPreset,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000") Double avgFps,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000") Double p1LowFps,
        @DecimalMin("0") @DecimalMax("150") Double maxCpuTempC,
        @DecimalMin("0") @DecimalMax("150") Double maxGpuTempC,
        @DecimalMin("0") @DecimalMax("100") Double avgCpuUsagePercent,
        @DecimalMin("0") @DecimalMax("100") Double avgGpuUsagePercent,
        @Size(max = ReportValues.NOTES_MAX) String notes) {
    public ReportValues toValues() {
        return new ReportValues(resolution, graphicsPreset, avgFps, p1LowFps, maxCpuTempC, maxGpuTempC,
                avgCpuUsagePercent, avgGpuUsagePercent, notes);
    }
}
