package com.ltz.hardware.api.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record GrantHardwareSyncRequest(@NotNull @PositiveOrZero Long profileVersion,
        @NotNull UUID deviceId, @NotNull @AssertTrue Boolean accepted) { }
