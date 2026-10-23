package com.ltz.hardware.api.dto.response;

public record SpecificationsResponse(Integer capacityGb, Integer clockMhz, Integer coreCount,
        Integer powerWatts, String memoryType, String socket, String interfaceType, String formFactor) { }
