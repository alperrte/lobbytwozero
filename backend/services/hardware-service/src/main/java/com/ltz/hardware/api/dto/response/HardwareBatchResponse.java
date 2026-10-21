package com.ltz.hardware.api.dto.response;

import com.ltz.hardware.domain.enums.DeviceType;
import java.util.List;
import java.util.UUID;

public record HardwareBatchResponse(List<Profile> profiles) {
    public record Profile(UUID profileId, long profileVersion, DeviceType deviceType,
            List<HardwareComponentResponse> components, Page page) { }
    public record Page(int number, int size, long totalElements, long totalPages,
            boolean hasNext, Integer nextPage) { }
}
