package com.ltz.hardware.application.service;

import com.ltz.hardware.api.dto.request.UpdateHardwarePrivacyRequest;
import com.ltz.hardware.api.dto.response.*;
import com.ltz.hardware.application.mapper.HardwareComponentMapper;
import com.ltz.hardware.application.port.CurrentActor;
import com.ltz.hardware.application.port.HardwareVisibilityPolicy;
import com.ltz.hardware.domain.entity.HardwareProfile;
import com.ltz.hardware.domain.entity.HardwareComponent;
import com.ltz.hardware.domain.enums.*;
import com.ltz.hardware.domain.exception.HardwareNotFoundException;
import com.ltz.hardware.infrastructure.persistence.repository.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HardwarePrivacyService {
    private final HardwareProfileRepository profiles;
    private final HardwareComponentRepository components;
    private final CurrentActor actor;
    private final HardwareVisibilityPolicy policy;
    private final HardwareComponentMapper mapper;

    public HardwarePrivacyService(HardwareProfileRepository profiles, HardwareComponentRepository components,
            CurrentActor actor, HardwareVisibilityPolicy policy, HardwareComponentMapper mapper) {
        this.profiles = profiles;
        this.components = components;
        this.actor = actor;
        this.policy = policy;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HardwarePrivacyResponse get(UUID profileId) {
        return privacy(profiles.findByIdAndUserId(profileId, actor.requireUserId())
                .orElseThrow(HardwareNotFoundException::new));
    }

    @Transactional
    public HardwarePrivacyResponse update(UUID profileId, UpdateHardwarePrivacyRequest request) {
        var profile = profiles.findOwnedForUpdate(profileId, actor.requireUserId())
                .orElseThrow(HardwareNotFoundException::new);
        profile.requireVersion(request.profileVersion());
        profile.configurePrivacy(request.visibility(), request.fields(), request.categories());
        profiles.flush();
        return privacy(profile);
    }

    private HardwarePrivacyResponse privacy(HardwareProfile profile) {
        return new HardwarePrivacyResponse(profile.getId(), profile.getVersion(), profile.getVisibility(),
                profile.getFieldVisibilities(), profile.getCategoryVisibilities());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SharedHardwareProfileResponse shared(UUID profileId, int page, int size) {
        var viewer = actor.requireUserId();
        var profile = profiles.findById(profileId).orElseThrow(HardwareNotFoundException::new);
        boolean owner = viewer.equals(profile.getUserId());
        var decision = owner ? new HardwareVisibilityPolicy.Decision(true, true)
                : externalDecision(viewer, profile.getUserId());
        if (decision == null || !decision.allowed()
                || (!owner && !profile.getVisibility().permits(decision.friends()))) {
            throw new HardwareNotFoundException();
        }
        var fields = visibleFields(profile, owner, decision.friends());
        var categories = Arrays.stream(ComponentCategory.values())
                .filter(c -> owner || profile.visibilityOf(c).permits(decision.friends())).toList();
        boolean hasComponentFields = fields.stream().anyMatch(f -> f != HardwarePrivacyField.PROFILE_NAME
                && f != HardwarePrivacyField.DEVICE_TYPE);
        var visible = categories.isEmpty() || !hasComponentFields
                ? Page.<HardwareComponent>empty()
                : components.findAllByProfileIdAndCategoryIn(profileId, categories,
                        PageRequest.of(page, size, Sort.by("createdAt", "id")));
        var items = visible.stream().map(c -> sharedComponent(c, fields)).toList();
        return new SharedHardwareProfileResponse(profileId,
                fields.contains(HardwarePrivacyField.PROFILE_NAME) ? profile.getName() : null,
                fields.contains(HardwarePrivacyField.DEVICE_TYPE) ? profile.getDeviceType() : null,
                items, new SharedHardwareProfileResponse.Page(page, size, visible.getTotalElements(), visible.getTotalPages()));
    }

    private Set<HardwarePrivacyField> visibleFields(HardwareProfile profile, boolean owner, boolean friends) {
        var fields = EnumSet.noneOf(HardwarePrivacyField.class);
        for (var field : HardwarePrivacyField.values()) {
            if (owner || profile.visibilityOf(field).permits(friends)) fields.add(field);
        }
        return fields;
    }

    private SharedHardwareComponentResponse sharedComponent(HardwareComponent component, Set<HardwarePrivacyField> fields) {
        var technical = fields.contains(HardwarePrivacyField.TECHNICAL_SPECIFICATIONS)
                ? mapper.toSpecificationsResponse(component.getSpecifications()) : null;
        return new SharedHardwareComponentResponse(component.getCategory(),
                fields.contains(HardwarePrivacyField.COMPONENT_MODEL) ? component.getModelName() : null,
                fields.contains(HardwarePrivacyField.COMPONENT_BRAND) ? component.getBrandName() : null,
                fields.contains(HardwarePrivacyField.COMPONENT_QUANTITY) ? component.getQuantity() : null, technical);
    }

    private HardwareVisibilityPolicy.Decision externalDecision(UUID viewer, UUID owner) {
        try {
            return policy.evaluate(viewer, owner);
        } catch (RuntimeException _) {
            // Provider failures must not distinguish a hidden resource from a missing one.
            return new HardwareVisibilityPolicy.Decision(false, false);
        }
    }
}
