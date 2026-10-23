package com.ltz.hardware.api.dto.response;

import com.ltz.hardware.domain.enums.CatalogStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record HardwareCampaignResponse(UUID id, UUID productId, String title, String sourceName,
        String sourceUrl, BigDecimal originalPrice, BigDecimal salePrice, String currency, BigDecimal discountPercent,
        Instant startsAt, Instant endsAt, Instant observedAt, CatalogStatus status, long version) { }
