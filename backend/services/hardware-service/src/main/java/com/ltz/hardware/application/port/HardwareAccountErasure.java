package com.ltz.hardware.application.port;

import java.util.UUID;

public interface HardwareAccountErasure {
    boolean erase(UUID accountId);
}
