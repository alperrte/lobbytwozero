package com.ltz.hardware.application.service;

import com.ltz.hardware.api.dto.request.SaveHardwareComponentRequest;
import com.ltz.hardware.api.dto.response.ComponentMutationResponse;
import com.ltz.hardware.application.mapper.HardwareComponentMapper;
import com.ltz.hardware.application.port.CurrentActor;
import com.ltz.hardware.domain.entity.HardwareComponent;
import com.ltz.hardware.domain.exception.HardwareNotFoundException;
import com.ltz.hardware.infrastructure.persistence.repository.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HardwareComponentService {
    private final HardwareProfileRepository profiles;
    private final HardwareComponentRepository components;
    private final HardwareProductRepository products;
    private final HardwareBrandRepository brands;
    private final CurrentActor actor;
    private final HardwareComponentMapper mapper;

    public HardwareComponentService(HardwareProfileRepository profiles, HardwareComponentRepository components,
            HardwareProductRepository products, HardwareBrandRepository brands, CurrentActor actor,
            HardwareComponentMapper mapper) {
        this.profiles = profiles;
        this.components = components;
        this.products = products;
        this.brands = brands;
        this.actor = actor;
        this.mapper = mapper;
    }

    @Transactional
    public ComponentMutationResponse add(UUID profileId, SaveHardwareComponentRequest request) {
        var profile = profiles.findOwnedForUpdate(profileId, actor.requireUserId())
                .orElseThrow(HardwareNotFoundException::new);
        profile.requireVersion(request.profileVersion());
        var component = resolve(profileId, request);
        profile.touch();
        components.saveAndFlush(component);
        return new ComponentMutationResponse(profile.getVersion(), mapper.toResponse(component));
    }

    @Transactional
    public ComponentMutationResponse update(UUID profileId, UUID componentId, SaveHardwareComponentRequest request) {
        var profile = profiles.findOwnedForUpdate(profileId, actor.requireUserId())
                .orElseThrow(HardwareNotFoundException::new);
        profile.requireVersion(request.profileVersion());
        var component = components.findByIdAndProfileId(componentId, profileId)
                .orElseThrow(HardwareNotFoundException::new);
        component.replaceWith(resolve(profileId, request));
        profile.touch();
        components.flush();
        return new ComponentMutationResponse(profile.getVersion(), mapper.toResponse(component));
    }

    @Transactional
    public long remove(UUID profileId, UUID componentId, long expectedVersion) {
        var profile = profiles.findOwnedForUpdate(profileId, actor.requireUserId())
                .orElseThrow(HardwareNotFoundException::new);
        profile.requireVersion(expectedVersion);
        var component = components.findByIdAndProfileId(componentId, profileId)
                .orElseThrow(HardwareNotFoundException::new);
        profile.touch();
        components.delete(component);
        components.flush();
        return profile.getVersion();
    }

    private HardwareComponent resolve(UUID profileId, SaveHardwareComponentRequest request) {
        var specs = mapper.toSpecifications(request.specifications());
        String modelName = request.modelName();
        String brandName = request.brandName();
        if (request.productId() == null) {
            if (request.brandId() != null) throw new IllegalArgumentException("Manuel marka kimliği kullanılamaz");
        } else {
            if (modelName != null || brandName != null) throw new IllegalArgumentException("Katalog ve manuel alanlar karışamaz");
            var product = products.findActiveForSelection(request.productId()).orElseThrow(HardwareNotFoundException::new);
            if (product.getCategory() != request.category()) throw new IllegalArgumentException("Kategori uyuşmuyor");
            modelName = product.getModel();
            if (specs == null) specs = product.getSpecifications();
            brandName = resolveBrandName(product.getId(), request.brandId());
        }
        var selection = new HardwareComponent.Selection(request.category(), request.productId(), request.brandId(),
                modelName, brandName, specs);
        return new HardwareComponent(profileId, selection, request.quantity());
    }

    private String resolveBrandName(UUID productId, UUID brandId) {
        if (brandId == null) return null;
        if (!products.supportsBrand(productId, brandId)) {
            throw new IllegalArgumentException("Marka bu model için tanımlı değil");
        }
        return brands.findActiveForSelection(java.util.Set.of(brandId)).stream().findFirst()
                .orElseThrow(HardwareNotFoundException::new).getName();
    }
}
