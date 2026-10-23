package com.ltz.hardware.application.service;

import com.ltz.hardware.api.dto.request.CreateHardwareProfileRequest;
import com.ltz.hardware.api.dto.request.UpdateHardwareProfileRequest;
import com.ltz.hardware.api.dto.response.HardwareProfileResponse;
import com.ltz.hardware.api.dto.response.HardwareProfilePageResponse;
import com.ltz.hardware.api.dto.response.HardwareProfileDetailResponse;
import com.ltz.hardware.application.mapper.HardwareProfileMapper;
import com.ltz.hardware.application.mapper.HardwareComponentMapper;
import com.ltz.hardware.application.port.CurrentActor;
import com.ltz.hardware.domain.entity.HardwareProfile;
import com.ltz.hardware.domain.exception.HardwareNotFoundException;
import java.util.UUID;
import com.ltz.hardware.infrastructure.persistence.repository.HardwareProfileRepository;
import com.ltz.hardware.infrastructure.persistence.repository.HardwareComponentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
public class HardwareProfileService {
    private final HardwareProfileRepository profiles;
    private final CurrentActor actor;
    private final HardwareProfileMapper mapper;
    private final HardwareComponentRepository components;
    private final HardwareComponentMapper componentMapper;

    public HardwareProfileService(HardwareProfileRepository profiles, CurrentActor actor,
                                  HardwareProfileMapper mapper, HardwareComponentRepository components,
                                  HardwareComponentMapper componentMapper) {
        this.profiles = profiles;
        this.actor = actor;
        this.mapper = mapper;
        this.components = components;
        this.componentMapper = componentMapper;
    }

    @Transactional
    public HardwareProfileResponse create(CreateHardwareProfileRequest request) {
        var profile = new HardwareProfile(actor.requireUserId(), request.name(), request.deviceType());
        return mapper.toResponse(profiles.saveAndFlush(profile));
    }

    @Transactional
    public HardwareProfileResponse update(UUID profileId, UpdateHardwareProfileRequest request) {
        var profile = profiles.findOwnedForUpdate(profileId, actor.requireUserId())
                .orElseThrow(HardwareNotFoundException::new);
        profile.requireVersion(request.profileVersion());
        profile.update(request.name(), request.deviceType());
        profiles.flush();
        return mapper.toResponse(profile);
    }

    @Transactional(readOnly = true)
    public HardwareProfilePageResponse list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Geçersiz sayfalama");
        var result = profiles.findAllByUserId(actor.requireUserId(),
                PageRequest.of(page, size, Sort.by("createdAt", "id")));
        return new HardwareProfilePageResponse(result.getContent().stream().map(mapper::toResponse).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HardwareProfileDetailResponse detail(UUID profileId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Geçersiz sayfalama");
        var profile = profiles.findByIdAndUserId(profileId, actor.requireUserId())
                .orElseThrow(HardwareNotFoundException::new);
        var result = components.findAllByProfileId(profileId, PageRequest.of(page, size, Sort.by("createdAt", "id")));
        return new HardwareProfileDetailResponse(mapper.toResponse(profile),
                result.getContent().stream().map(componentMapper::toResponse).toList(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public void delete(UUID profileId, long expectedVersion) {
        var profile = profiles.findOwnedForUpdate(profileId, actor.requireUserId())
                .orElseThrow(HardwareNotFoundException::new);
        profile.requireVersion(expectedVersion);
        profiles.delete(profile);
        profiles.flush();
    }
}
