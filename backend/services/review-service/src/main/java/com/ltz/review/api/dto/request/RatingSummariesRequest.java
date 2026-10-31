package com.ltz.review.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record RatingSummariesRequest(@NotNull @Size(max = 50) List<@NotNull UUID> gameIds) {}
