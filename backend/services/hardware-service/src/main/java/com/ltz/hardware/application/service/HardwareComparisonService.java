package com.ltz.hardware.application.service;

import com.ltz.hardware.api.dto.request.*;
import com.ltz.hardware.api.dto.response.*;
import com.ltz.hardware.application.mapper.HardwareComponentMapper;
import com.ltz.hardware.application.port.CurrentActor;
import com.ltz.hardware.domain.entity.*;
import com.ltz.hardware.domain.enums.*;
import com.ltz.hardware.domain.exception.HardwareNotFoundException;
import com.ltz.hardware.infrastructure.persistence.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class HardwareComparisonService {
    private final HardwareProfileRepository profiles;
    private final HardwareComponentRepository components;
    private final HardwareProductRepository products;
    private final CurrentActor actor;
    private final HardwareComponentMapper mapper;

    public HardwareComparisonService(HardwareProfileRepository profiles, HardwareComponentRepository components,
            HardwareProductRepository products, CurrentActor actor, HardwareComponentMapper mapper) {
        this.profiles = profiles;
        this.components = components;
        this.products = products;
        this.actor = actor;
        this.mapper = mapper;
    }

    public HardwareComparisonResponse<HardwareProfileComparisonItem> profiles(CompareHardwareIdsRequest request) {
        var userId = actor.requireUserId();
        var left = owned(request.leftId(), userId);
        var right = owned(request.rightId(), userId);
        var counts = components.countsByProfiles(List.of(left.getId(), right.getId()));
        return new HardwareComparisonResponse<>(summary(left, counts), summary(right, counts));
    }

    private HardwareProfile owned(UUID id, UUID userId) {
        return profiles.findByIdAndUserId(id, userId).orElseThrow(HardwareNotFoundException::new);
    }

    private HardwareProfileComparisonItem summary(HardwareProfile profile,
            List<HardwareComponentRepository.ProfileCategoryCount> counts) {
        var categories = new EnumMap<ComponentCategory, ComponentCountResponse>(ComponentCategory.class);
        for (var category : ComponentCategory.values()) categories.put(category, new ComponentCountResponse(0, 0));
        var ram = new HardwareProfileComparisonItem.Capacity(0, 0);
        var storage = new HardwareProfileComparisonItem.Capacity(0, 0);
        for (var count : counts) {
            if (!count.getProfileId().equals(profile.getId())) continue;
            categories.put(count.getCategory(), new ComponentCountResponse(count.getEntries(), count.getUnits()));
            var capacity = new HardwareProfileComparisonItem.Capacity(count.getKnownCapacityGb(), count.getUnitsWithoutCapacity());
            if (count.getCategory() == ComponentCategory.RAM) ram = capacity;
            if (count.getCategory() == ComponentCategory.STORAGE) storage = capacity;
        }
        return new HardwareProfileComparisonItem(profile.getId(), profile.getDeviceType(), categories, ram, storage);
    }

    public HardwareComparisonResponse<HardwareTechnicalComparisonItem> components(CompareHardwareComponentsRequest request) {
        var userId = actor.requireUserId();
        var left = component(request.left(), userId);
        var right = component(request.right(), userId);
        sameCategory(left.getCategory(), right.getCategory());
        return new HardwareComparisonResponse<>(technical(left), technical(right));
    }

    private HardwareComponent component(CompareHardwareComponentsRequest.Reference ref, UUID userId) {
        owned(ref.profileId(), userId);
        return components.findByIdAndProfileId(ref.componentId(), ref.profileId()).orElseThrow(HardwareNotFoundException::new);
    }

    private HardwareTechnicalComparisonItem technical(HardwareComponent component) {
        return new HardwareTechnicalComparisonItem(component.getId(), component.getCategory(), component.getModelName(),
                component.getQuantity(), mapper.toSpecificationsResponse(component.getSpecifications()));
    }

    public HardwareComparisonResponse<HardwareTechnicalComparisonItem> catalogProducts(CompareHardwareIdsRequest request) {
        actor.requireUserId();
        var left = activeProduct(request.leftId());
        var right = activeProduct(request.rightId());
        sameCategory(left.getCategory(), right.getCategory());
        return new HardwareComparisonResponse<>(technical(left), technical(right));
    }

    private HardwareProduct activeProduct(UUID id) {
        return products.findByIdAndStatus(id, CatalogStatus.ACTIVE).orElseThrow(HardwareNotFoundException::new);
    }

    private HardwareTechnicalComparisonItem technical(HardwareProduct product) {
        return new HardwareTechnicalComparisonItem(product.getId(), product.getCategory(), product.getModel(), null,
                mapper.toSpecificationsResponse(product.getSpecifications()));
    }

    private void sameCategory(ComponentCategory left, ComponentCategory right) {
        if (left != right) throw new IllegalArgumentException("Aynı kategoride iki bileşen gerekli");
    }
}
