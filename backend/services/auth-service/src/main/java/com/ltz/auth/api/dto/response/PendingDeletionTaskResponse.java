package com.ltz.auth.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Silme koordinasyonunda tamamlanmamış görev. Kişisel veri yoktur; {@code stuck} eşik aşıldığını belirtir. */
public record PendingDeletionTaskResponse(UUID requestId, UUID accountId, String service, int attempts,
                                          String lastError, Instant nextAttemptAt, Instant createdAt,
                                          boolean stuck) {
}
