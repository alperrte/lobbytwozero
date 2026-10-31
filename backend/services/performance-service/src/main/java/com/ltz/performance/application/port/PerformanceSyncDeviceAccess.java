package com.ltz.performance.application.port;

public interface PerformanceSyncDeviceAccess {
    String PERMISSION = "performance:sync:write";
    String AUDIENCE = "performance-service";

    AuthenticatedPerformanceDevice requireDevice();
}
