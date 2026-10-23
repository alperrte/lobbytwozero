package com.ltz.hardware.application.port;

public interface HardwareSyncDeviceAccess {
    String PERMISSION = "hardware:sync:write";
    AuthenticatedHardwareDevice requireDevice();
}
