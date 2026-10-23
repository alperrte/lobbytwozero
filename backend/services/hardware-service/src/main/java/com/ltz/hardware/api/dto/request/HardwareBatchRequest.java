package com.ltz.hardware.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record HardwareBatchRequest(@NotNull @Size(min = 1, max = 20)
        List<@NotNull @Valid ProfilePage> profiles) {
    public record ProfilePage(@NotNull UUID profileId, @NotNull @Min(0) @Max(10000) Integer page,
            @NotNull @Min(1) @Max(100) Integer size) { }
}
