package com.ltz.hardware.api.dto.response;

import com.ltz.hardware.domain.enums.DeviceType;
import java.time.Instant;
import java.util.UUID;

public record HardwareProfileResponse(UUID id, String name, DeviceType deviceType,
                                      String source, long version, Instant createdAt, Instant updatedAt) { }
