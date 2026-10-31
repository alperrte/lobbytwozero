package com.ltz.performance.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Bağlanacak cihazın Auth Service'teki kimliği. Sahiplik ve etkinlik Auth'tan doğrulanır. */
public record LinkDeviceRequest(@NotNull UUID deviceId) { }
