package com.ltz.hardware.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CompareHardwareIdsRequest(@NotNull UUID leftId, @NotNull UUID rightId) { }
