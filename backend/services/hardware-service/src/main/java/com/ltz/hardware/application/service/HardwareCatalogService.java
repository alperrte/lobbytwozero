package com.ltz.hardware.application.service;

import com.ltz.hardware.api.dto.request.*;
import com.ltz.hardware.api.dto.response.*;
import com.ltz.hardware.application.mapper.HardwareCatalogMapper;
import com.ltz.hardware.application.mapper.HardwareComponentMapper;
import com.ltz.hardware.application.port.CatalogWriteAccess;
import com.ltz.hardware.application.port.CurrentActor;
import com.ltz.hardware.domain.entity.HardwareBrand;
import com.ltz.hardware.domain.entity.HardwareProduct;
import com.ltz.hardware.domain.enums.CatalogStatus;
import com.ltz.hardware.domain.exception.HardwareNotFoundException;
import com.ltz.hardware.infrastructure.persistence.repository.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HardwareCatalogService {
    private final HardwareProductRepository products;
    private final HardwareBrandRepository brands;
    private final HardwareCatalogAuditRepository audit;
    private final CatalogWriteAccess access;
    private final CurrentActor actor;
    private final HardwareCatalogMapper mapper;
    private final HardwareComponentMapper componentMapper;

    public HardwareCatalogService(HardwareProductRepository products, HardwareBrandRepository brands,
            HardwareCatalogAuditRepository audit, CatalogWriteAccess access, CurrentActor actor,
            HardwareCatalogMapper mapper, HardwareComponentMapper componentMapper) {
        this.products = products;
        this.brands = brands;
        this.audit = audit;
        this.access = access;
        this.actor = actor;
        this.mapper = mapper;
        this.componentMapper = componentMapper;
    }

    @Transactional
    public HardwareProductResponse create(CreateHardwareProductRequest request) {
        UUID actorId = access.requireWriter();
        var product = new HardwareProduct(request.category(), request.model(),
                componentMapper.toSpecifications(request.specifications()), selectBrands(request.brandIds()));
        product = products.saveAndFlush(product);
        audit.append(actorId, "CREATE_PRODUCT", product.getId(), request.reason());
        return mapper.product(product);
    }

    @Transactional
    public HardwareProductResponse update(UUID id, UpdateHardwareProductRequest request) {
        UUID actorId = access.requireWriter();
        var product = findForUpdate(id);
        product.requireVersion(request.version());
        product.update(request.model(), componentMapper.toSpecifications(request.specifications()),
                selectBrands(request.brandIds()));
        products.flush();
        audit.append(actorId, "UPDATE_PRODUCT", id, request.reason());
        return mapper.product(product);
    }

    @Transactional
    public void archive(UUID id, ArchiveCatalogEntryRequest request) {
        UUID actorId = access.requireWriter();
        var product = findForUpdate(id);
        product.requireVersion(request.version());
        product.archive();
        products.flush();
        audit.append(actorId, "ARCHIVE_PRODUCT", id, request.reason());
    }

    @Transactional(readOnly = true)
    public HardwareProductResponse detail(UUID id) {
        actor.requireUserId();
        return mapper.product(products.findByIdAndStatus(id, CatalogStatus.ACTIVE)
                .orElseThrow(HardwareNotFoundException::new));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HardwareCatalogPageResponse<HardwareProductResponse> list(HardwareCatalogFilter filter, int page, int size) {
        actor.requireUserId();
        if (!filter.isRangesValid()) throw new IllegalArgumentException("Geçersiz teknik aralık");
        var criteria = HardwareCatalogSearch.matching(filter.q(), CatalogStatus.ACTIVE, filter.category(),
                filter.brandId(), filter.brand(), java.util.List.of(
                        new HardwareCatalogSearch.NumericRange("capacityGb", filter.minCapacityGb(), filter.maxCapacityGb()),
                        new HardwareCatalogSearch.NumericRange("clockMhz", filter.minClockMhz(), filter.maxClockMhz()),
                        new HardwareCatalogSearch.NumericRange("coreCount", filter.minCoreCount(), filter.maxCoreCount()),
                        new HardwareCatalogSearch.NumericRange("powerWatts", filter.minPowerWatts(), filter.maxPowerWatts())),
                java.util.List.of(
                        new HardwareCatalogSearch.TextValue("memoryType", filter.memoryType()),
                        new HardwareCatalogSearch.TextValue("socket", filter.socket()),
                        new HardwareCatalogSearch.TextValue("interfaceType", filter.interfaceType()),
                        new HardwareCatalogSearch.TextValue("formFactor", filter.formFactor())));
        var result = products.findAll(criteria,
                PageRequest.of(page, size, Sort.by("normalizedModel", "id")));
        if (result.isEmpty()) return new HardwareCatalogPageResponse<>(java.util.List.of(), page, size,
                result.getTotalElements(), result.getTotalPages());
        var hydrated = products.findWithBrands(result.stream().map(HardwareProduct::getId).toList()).stream()
                .collect(Collectors.toMap(HardwareProduct::getId, Function.identity()));
        var items = result.stream().map(p -> mapper.product(hydrated.get(p.getId()))).toList();
        return new HardwareCatalogPageResponse<>(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    private Set<HardwareBrand> selectBrands(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Set.of();
        var selected = brands.findActiveForSelection(ids);
        if (selected.size() != ids.size()) throw new HardwareNotFoundException();
        return new HashSet<>(selected);
    }

    private HardwareProduct findForUpdate(UUID id) {
        return products.findForUpdate(id).orElseThrow(HardwareNotFoundException::new);
    }
}
