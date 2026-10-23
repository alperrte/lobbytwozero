package com.ltz.hardware.api.dto.response;

import com.ltz.hardware.domain.enums.*;
import java.util.Map;
import java.util.UUID;

public record HardwarePrivacyResponse(UUID profileId, long profileVersion, HardwareVisibility visibility,
        Map<HardwarePrivacyField, HardwareVisibility> fields,
        Map<ComponentCategory, HardwareVisibility> categories) { }
