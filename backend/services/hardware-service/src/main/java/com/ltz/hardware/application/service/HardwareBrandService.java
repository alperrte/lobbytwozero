package com.ltz.hardware.application.service;

import com.ltz.hardware.api.dto.request.*;
import com.ltz.hardware.api.dto.response.*;
import com.ltz.hardware.application.mapper.HardwareCatalogMapper;
import com.ltz.hardware.application.port.CatalogWriteAccess;
import com.ltz.hardware.application.port.CurrentActor;
import com.ltz.hardware.domain.entity.HardwareBrand;
import com.ltz.hardware.domain.enums.CatalogStatus;
import com.ltz.hardware.domain.exception.HardwareNotFoundException;
import com.ltz.hardware.infrastructure.persistence.repository.HardwareBrandRepository;
import com.ltz.hardware.infrastructure.persistence.repository.HardwareCatalogAuditRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HardwareBrandService {
    private final HardwareBrandRepository brands;
    private final HardwareCatalogAuditRepository audit;
    private final CatalogWriteAccess access;
    private final CurrentActor actor;
    private final HardwareCatalogMapper mapper;

    public HardwareBrandService(HardwareBrandRepository brands, HardwareCatalogAuditRepository audit,
            CatalogWriteAccess access, CurrentActor actor, HardwareCatalogMapper mapper) {
        this.brands = brands;
        this.audit = audit;
        this.access = access;
        this.actor = actor;
        this.mapper = mapper;
    }

    @Transactional
    public HardwareBrandResponse create(CreateHardwareBrandRequest request) {
        UUID actorId = access.requireWriter();
        var brand = brands.saveAndFlush(new HardwareBrand(request.name()));
        audit.append(actorId, "CREATE_BRAND", brand.getId(), request.reason());
        return mapper.brand(brand);
    }

    @Transactional
    public HardwareBrandResponse update(UUID id, UpdateHardwareBrandRequest request) {
        UUID actorId = access.requireWriter();
        var brand = findForUpdate(id);
        brand.requireVersion(request.version());
        brand.rename(request.name());
        brands.flush();
        audit.append(actorId, "UPDATE_BRAND", id, request.reason());
        return mapper.brand(brand);
    }

    @Transactional
    public void archive(UUID id, ArchiveCatalogEntryRequest request) {
        UUID actorId = access.requireWriter();
        var brand = findForUpdate(id);
        brand.requireVersion(request.version());
        brand.archive();
        brands.flush();
        audit.append(actorId, "ARCHIVE_BRAND", id, request.reason());
    }

    @Transactional(readOnly = true)
    public HardwareBrandResponse detail(UUID id) {
        actor.requireUserId();
        return mapper.brand(brands.findByIdAndStatus(id, CatalogStatus.ACTIVE)
                .orElseThrow(HardwareNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public HardwareCatalogPageResponse<HardwareBrandResponse> list(int page, int size) {
        actor.requireUserId();
        var result = brands.findByStatus(CatalogStatus.ACTIVE,
                PageRequest.of(page, size, Sort.by("normalizedName", "id")));
        return new HardwareCatalogPageResponse<>(result.map(mapper::brand).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    private HardwareBrand findForUpdate(UUID id) {
        return brands.findForUpdate(id).orElseThrow(HardwareNotFoundException::new);
    }
}
