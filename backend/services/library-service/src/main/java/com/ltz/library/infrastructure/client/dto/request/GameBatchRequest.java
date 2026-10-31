package com.ltz.library.infrastructure.client.dto.request;

import java.util.List;
import java.util.UUID;

public record GameBatchRequest(List<UUID> gameIds) { }
