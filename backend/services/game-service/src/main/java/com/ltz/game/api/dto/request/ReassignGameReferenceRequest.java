package com.ltz.game.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReassignGameReferenceRequest(
        @NotBlank @Pattern(regexp="STEAM") String provider,
        @NotBlank @Size(max=128) String externalId,
        @NotNull UUID expectedGameId, @NotNull UUID targetGameId,
        @NotBlank @Size(min=8,max=1000) String evidence) { }
