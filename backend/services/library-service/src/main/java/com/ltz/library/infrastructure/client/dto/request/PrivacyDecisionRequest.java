package com.ltz.library.infrastructure.client.dto.request;

import java.util.List;
import java.util.UUID;

/** User {@code POST /internal/v1/privacy/decisions} gövdesi; {@code field} LIBRARY veya RECOMMENDATION'dır. */
public record PrivacyDecisionRequest(UUID viewerId, String field, List<UUID> ownerIds) { }
