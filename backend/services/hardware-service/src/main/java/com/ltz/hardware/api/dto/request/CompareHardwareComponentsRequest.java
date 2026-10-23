package com.ltz.hardware.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CompareHardwareComponentsRequest(@NotNull @Valid Reference left,
        @NotNull @Valid Reference right) {
    public record Reference(@NotNull UUID profileId, @NotNull UUID componentId) { }
}
