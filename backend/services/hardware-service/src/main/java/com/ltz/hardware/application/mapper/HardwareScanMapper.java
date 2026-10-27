package com.ltz.hardware.application.mapper;

import com.ltz.hardware.api.dto.request.HardwareScanRequest;
import com.ltz.hardware.api.dto.response.HardwareScanPreviewResponse;
import com.ltz.hardware.domain.entity.HardwareScan;
import org.springframework.stereotype.Component;

@Component
public class HardwareScanMapper {
    private final HardwareComponentMapper components;
    public HardwareScanMapper(HardwareComponentMapper components) { this.components = components; }

    public HardwareScan toScan(HardwareScanRequest request) {
        return new HardwareScan(request.schemaVersion(), request.capturedAt(), request.components().stream()
                .map(c -> new HardwareScan.Component(c.componentKey(), c.category(), c.modelName(), c.brandName(),
                        c.quantity(), components.toSpecifications(c.specifications()))).toList());
    }

    public HardwareScanPreviewResponse preview(HardwareScan scan, long profileVersion) {
        return new HardwareScanPreviewResponse(profileVersion, scan.schemaVersion(), scan.capturedAt(),
                scan.components().size(), false, scan.components().stream().map(c ->
                new HardwareScanPreviewResponse.Component(c.componentKey(), c.category(), c.modelName(), c.brandName(),
                        c.quantity(), components.toSpecificationsResponse(c.specifications()))).toList());
    }
}
