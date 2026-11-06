package com.ltz.content.api.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** İçeriği LTZ oyun kataloğundaki bir oyuna bağlar; oyun game-service'te doğrulanır. */
public record LinkGameRequest(@NotNull UUID gameId) {
}
