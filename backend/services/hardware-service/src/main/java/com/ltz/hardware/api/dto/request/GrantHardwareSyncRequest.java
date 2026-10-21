package com.ltz.hardware.api.dto.request;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record GrantHardwareSyncRequest(@NotNull @PositiveOrZero Long profileVersion,
        @NotNull UUID deviceId, @NotNull @AssertTrue Boolean accepted) { }
