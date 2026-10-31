package com.ltz.performance.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Oturum zamanları sunucudan alınır; istemci zamanı veya durumu kabul edilmez (bilinmeyen alan reddedilir). */
public record StartSessionRequest(@NotNull UUID gameId, UUID hardwareProfileId, UUID deviceLinkId) { }
