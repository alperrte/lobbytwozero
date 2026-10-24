package com.ltz.game.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** En fazla 50 gameId; tekrarlar tek sayılır. */
public record GameBatchRequest(@NotNull @Size(min=1, max=50) List<@NotNull UUID> gameIds) { }
