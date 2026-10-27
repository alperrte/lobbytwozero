package com.ltz.game.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record GrantDeveloperRequest(@NotNull UUID developerUserId, @NotBlank @Size(max=1000) String reason) { }
