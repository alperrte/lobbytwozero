package com.ltz.game.api.dto.request;

import com.ltz.game.domain.enums.DeveloperMediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeveloperMediaRequest(@NotNull DeveloperMediaType type, @NotBlank @Size(max=1024) String url, @Size(max=200) String altText) { }
