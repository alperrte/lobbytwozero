package com.ltz.hardware.application.mapper;

import com.ltz.hardware.api.dto.response.HardwareCampaignResponse;
import com.ltz.hardware.domain.entity.HardwareCampaign;
import org.springframework.stereotype.Component;

@Component
public class HardwareCampaignMapper {
    public HardwareCampaignResponse toResponse(HardwareCampaign campaign) {
        return new HardwareCampaignResponse(campaign.getId(), campaign.getProductId(), campaign.getTitle(),
                campaign.getSourceName(), campaign.getSourceUrl(), campaign.getOriginalPrice(), campaign.getSalePrice(),
                campaign.getCurrency(), campaign.discountPercent(), campaign.getStartsAt(), campaign.getEndsAt(),
                campaign.getObservedAt(), campaign.getStatus(), campaign.getVersion());
    }
}
