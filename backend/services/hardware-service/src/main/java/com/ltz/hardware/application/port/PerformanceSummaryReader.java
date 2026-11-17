package com.ltz.hardware.application.port;

import com.ltz.hardware.api.dto.response.HardwarePerformanceSummaryResponse;
import java.util.UUID;

public interface PerformanceSummaryReader {
    HardwarePerformanceSummaryResponse read(UUID accountId, UUID hardwareProfileId);
}
