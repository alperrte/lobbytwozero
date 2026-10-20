package com.ltz.hardware.application.mapper;

import com.ltz.hardware.api.dto.response.HardwareProfileResponse;
import com.ltz.hardware.domain.entity.HardwareProfile;
import org.springframework.stereotype.Component;

@Component
public class HardwareProfileMapper {
    public HardwareProfileResponse toResponse(HardwareProfile profile) {
        return new HardwareProfileResponse(profile.getId(), profile.getName(), profile.getDeviceType(),
                profile.getSource(), profile.getVersion(), profile.getCreatedAt(), profile.getUpdatedAt());
    }
}
