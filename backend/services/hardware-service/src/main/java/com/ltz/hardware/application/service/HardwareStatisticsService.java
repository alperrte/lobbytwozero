package com.ltz.hardware.application.service;

import com.ltz.hardware.api.dto.response.ComponentCountResponse;
import com.ltz.hardware.api.dto.response.HardwareStatisticsResponse;
import com.ltz.hardware.application.port.CurrentActor;
import com.ltz.hardware.domain.enums.ComponentCategory;
import com.ltz.hardware.domain.enums.DeviceType;
import com.ltz.hardware.infrastructure.persistence.repository.HardwareComponentRepository;
import com.ltz.hardware.infrastructure.persistence.repository.HardwareProfileRepository;
import java.util.EnumMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HardwareStatisticsService {
    private final HardwareProfileRepository profiles;
    private final HardwareComponentRepository components;
    private final CurrentActor actor;

    public HardwareStatisticsService(HardwareProfileRepository profiles,
            HardwareComponentRepository components, CurrentActor actor) {
        this.profiles = profiles;
        this.components = components;
        this.actor = actor;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HardwareStatisticsResponse statistics() {
        var userId = actor.requireUserId();
        var devices = new EnumMap<DeviceType, Long>(DeviceType.class);
        for (var type : DeviceType.values()) devices.put(type, 0L);
        for (var count : profiles.countsByDeviceType(userId)) devices.put(count.getDeviceType(), count.getTotal());
        var categories = new EnumMap<ComponentCategory, ComponentCountResponse>(ComponentCategory.class);
        for (var type : ComponentCategory.values()) categories.put(type, new ComponentCountResponse(0, 0));
        long entries = 0;
        long units = 0;
        long ram = 0;
        long unknownRam = 0;
        long storage = 0;
        long unknownStorage = 0;
        for (var count : components.countsByCategory(userId)) {
            categories.put(count.getCategory(), new ComponentCountResponse(count.getEntries(), count.getUnits()));
            entries += count.getEntries();
            units += count.getUnits();
            if (count.getCategory() == ComponentCategory.RAM) {
                ram = count.getKnownCapacityGb();
                unknownRam = count.getUnitsWithoutCapacity();
            }
            if (count.getCategory() == ComponentCategory.STORAGE) {
                storage = count.getKnownCapacityGb();
                unknownStorage = count.getUnitsWithoutCapacity();
            }
        }
        return new HardwareStatisticsResponse(devices.values().stream().mapToLong(Long::longValue).sum(),
                devices, entries, units, categories, ram, unknownRam, storage, unknownStorage);
    }
}
