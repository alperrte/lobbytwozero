package com.ltz.game.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** REC-06/16: en fazla 100 gameId; tekrar eden kimlik geçersiz sayılır (her kimlik tam bir kez cevaplanır). */
public record RecommendationMetadataBatchRequest(@NotNull @Size(min = 1, max = 100) List<@NotNull UUID> gameIds) { }
