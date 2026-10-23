package com.ltz.hardware.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ltz.hardware.domain.enums.DeviceType;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SharedHardwareProfileResponse(UUID profileId, String name, DeviceType deviceType,
        List<SharedHardwareComponentResponse> components, Page page) {
    public record Page(int number, int size, long totalElements, int totalPages) { }
}
