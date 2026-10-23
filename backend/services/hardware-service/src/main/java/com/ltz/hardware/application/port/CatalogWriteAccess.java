package com.ltz.hardware.application.port;

import java.util.UUID;

public interface CatalogWriteAccess {
    String PERMISSION = "hardware:catalog:write";
    UUID requireWriter();
}
