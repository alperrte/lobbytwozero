package com.ltz.community.api.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record LinkGameRequest(@NotNull UUID gameId) {
}
