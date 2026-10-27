package com.ltz.hardware.application.service;

import com.ltz.hardware.api.dto.request.ArchiveCatalogEntryRequest;
import com.ltz.hardware.api.dto.request.SaveHardwareCampaignRequest;
import com.ltz.hardware.api.dto.response.*;
import com.ltz.hardware.application.mapper.HardwareCampaignMapper;
import com.ltz.hardware.application.port.CatalogWriteAccess;
import com.ltz.hardware.application.port.CurrentActor;
import com.ltz.hardware.domain.entity.HardwareCampaign;
import com.ltz.hardware.domain.exception.HardwareNotFoundException;
import com.ltz.hardware.infrastructure.persistence.repository.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HardwareCampaignService {
    private final HardwareCampaignRepository campaigns;
    private final HardwareProductRepository products;
    private final HardwareCatalogAuditRepository audit;
    private final CatalogWriteAccess access;
    private final CurrentActor actor;
    private final HardwareCampaignMapper mapper;

    public HardwareCampaignService(HardwareCampaignRepository campaigns, HardwareProductRepository products,
            HardwareCatalogAuditRepository audit, CatalogWriteAccess access, CurrentActor actor, HardwareCampaignMapper mapper) {
        this.campaigns = campaigns;
        this.products = products;
        this.audit = audit;
        this.access = access;
        this.actor = actor;
        this.mapper = mapper;
    }

    @Transactional
    public HardwareCampaignResponse create(SaveHardwareCampaignRequest request) {
        UUID actorId = access.requireWriter();
        if (request.productId() == null || request.version() != null) {
            throw new IllegalArgumentException("Oluşturma ürün kimliği gerektirir");
        }
        requireProduct(request.productId());
        var campaign = new HardwareCampaign(request.productId(), terms(request), Instant.now());
        campaign = campaigns.saveAndFlush(campaign);
        audit.append(actorId, "CREATE_CAMPAIGN", campaign.getId(), request.reason());
        return mapper.toResponse(campaign);
    }

    @Transactional
    public HardwareCampaignResponse update(UUID id, SaveHardwareCampaignRequest request) {
        UUID actorId = access.requireWriter();
        if (request.version() == null || request.productId() != null) {
            throw new IllegalArgumentException("Güncelleme sürüm gerektirir; ürün değiştirilemez");
        }
        var campaign = campaigns.findForUpdate(id).orElseThrow(HardwareNotFoundException::new);
        campaign.requireVersion(request.version());
        requireProduct(campaign.getProductId());
        campaign.update(terms(request), Instant.now());
        campaigns.flush();
        audit.append(actorId, "UPDATE_CAMPAIGN", id, request.reason());
        return mapper.toResponse(campaign);
    }

    @Transactional
    public void archive(UUID id, ArchiveCatalogEntryRequest request) {
        UUID actorId = access.requireWriter();
        var campaign = campaigns.findForUpdate(id).orElseThrow(HardwareNotFoundException::new);
        campaign.requireVersion(request.version());
        campaign.archive(Instant.now());
        campaigns.flush();
        audit.append(actorId, "ARCHIVE_CAMPAIGN", id, request.reason());
    }

    @Transactional(readOnly = true)
    public HardwareCampaignResponse detail(UUID id) {
        actor.requireUserId();
        return mapper.toResponse(campaigns.findVisibleById(id, Instant.now()).orElseThrow(HardwareNotFoundException::new));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HardwareCatalogPageResponse<HardwareCampaignResponse> list(UUID productId, int page, int size) {
        actor.requireUserId();
        var result = campaigns.findVisible(Instant.now(), productId,
                PageRequest.of(page, size, Sort.by("startsAt", "id")));
        return new HardwareCatalogPageResponse<>(result.map(mapper::toResponse).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    private void requireProduct(UUID id) {
        products.findActiveForSelection(id).orElseThrow(HardwareNotFoundException::new);
    }

    private static HardwareCampaign.Terms terms(SaveHardwareCampaignRequest request) {
        return new HardwareCampaign.Terms(request.title(), request.sourceName(), request.sourceUrl(),
                request.originalPrice(), request.salePrice(), request.currency(),
                request.startsAt(), request.endsAt(), request.observedAt());
    }
}
