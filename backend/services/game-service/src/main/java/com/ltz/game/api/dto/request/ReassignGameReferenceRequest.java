package com.ltz.game.api.dto.request;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record ReassignGameReferenceRequest(
        @NotBlank @Pattern(regexp="STEAM") String provider,
        @NotBlank @Size(max=128) String externalId,
        @NotNull UUID expectedGameId, @NotNull UUID targetGameId,
        @NotBlank @Size(min=8,max=1000) String evidence) { }
