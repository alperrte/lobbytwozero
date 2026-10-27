package com.ltz.hardware.application.service;

import com.ltz.hardware.api.dto.request.HardwareBatchRequest;
import com.ltz.hardware.api.dto.response.HardwareBatchResponse;
import com.ltz.hardware.application.mapper.HardwareComponentMapper;
import com.ltz.hardware.application.port.CurrentActor;
import com.ltz.hardware.domain.entity.HardwareComponent;
import com.ltz.hardware.domain.entity.HardwareProfile;
import com.ltz.hardware.domain.exception.HardwareNotFoundException;
import com.ltz.hardware.infrastructure.persistence.repository.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HardwareBatchService {
    private final HardwareProfileRepository profiles;
    private final HardwareComponentRepository components;
    private final HardwareBatchQuery query;
    private final CurrentActor actor;
    private final HardwareComponentMapper mapper;

    public HardwareBatchService(HardwareProfileRepository profiles, HardwareComponentRepository components,
            HardwareBatchQuery query, CurrentActor actor, HardwareComponentMapper mapper) {
        this.profiles = profiles;
        this.components = components;
        this.query = query;
        this.actor = actor;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HardwareBatchResponse read(HardwareBatchRequest request) {
        var userId = actor.requireUserId();
        var ids = request.profiles().stream().map(HardwareBatchRequest.ProfilePage::profileId).toList();
        if (new HashSet<>(ids).size() != ids.size()
                || request.profiles().stream().mapToInt(HardwareBatchRequest.ProfilePage::size).sum() > 200) {
            throw new IllegalArgumentException("Tekil profiller ve toplam en fazla 200 satır gerekli");
        }
        var owned = profiles.findAllByIdInAndUserId(ids, userId).stream()
                .collect(Collectors.toMap(HardwareProfile::getId, Function.identity()));
        if (owned.size() != ids.size()) throw new HardwareNotFoundException();
        var totals = components.totalsByProfiles(ids).stream().collect(Collectors.toMap(
                HardwareComponentRepository.ProfileTotal::getProfileId, HardwareComponentRepository.ProfileTotal::getTotal));
        var windows = request.profiles().stream()
                .map(p -> new HardwareBatchQuery.Window(p.profileId(), p.page(), p.size())).toList();
        var selected = query.componentIds(windows);
        var selectedIds = selected.values().stream().flatMap(Collection::stream).toList();
        var records = components.findAllById(selectedIds).stream()
                .collect(Collectors.toMap(HardwareComponent::getId, Function.identity()));
        var result = request.profiles().stream().map(p -> {
            var profile = owned.get(p.profileId());
            var items = selected.getOrDefault(p.profileId(), List.of()).stream()
                    .map(records::get).map(mapper::toResponse).toList();
            long total = totals.getOrDefault(p.profileId(), 0L);
            long totalPages = (total + p.size() - 1) / p.size();
            boolean hasNext = (long) p.page() + 1 < totalPages;
            return new HardwareBatchResponse.Profile(p.profileId(), profile.getVersion(), profile.getDeviceType(), items,
                    new HardwareBatchResponse.Page(p.page(), p.size(), total, totalPages, hasNext, hasNext ? p.page() + 1 : null));
        }).toList();
        return new HardwareBatchResponse(result);
    }
}
