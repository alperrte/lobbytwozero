package com.ltz.hardware.api.dto.response;

import com.ltz.hardware.domain.entity.HardwareCatalogContent.Group;
import com.ltz.hardware.domain.entity.HardwareCatalogContent.Value;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record HardwareCatalogSourceResponse(String provider, String externalProductId, String brand,
        String manufacturerPartNumber, List<String> gtins, String locale, Instant sourceUpdatedAt,
        Instant lastSuccessfulSync, String quality, Map<String, Value> typedSpecifications,
        List<Group> featureGroups, List<Image> images) {
    public record Image(UUID id, String url, String alt, int width, int height, boolean primary, boolean rightsVerified) { }
}
