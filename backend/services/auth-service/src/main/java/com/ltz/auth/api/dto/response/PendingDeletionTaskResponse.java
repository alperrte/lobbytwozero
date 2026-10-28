package com.ltz.auth.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.application.service.PendingDeletionTask;

/** Silme koordinasyonunda tamamlanmamış görev. Kişisel veri yoktur; {@code stuck} eşik aşıldığını belirtir. */
public record PendingDeletionTaskResponse(UUID requestId, UUID accountId, String service, int attempts,
                                          String lastError, Instant nextAttemptAt, Instant createdAt,
                                          boolean stuck) {

    public static PendingDeletionTaskResponse from(PendingDeletionTask task) {
        return new PendingDeletionTaskResponse(task.requestId(), task.accountId(), task.service(), task.attempts(),
                task.lastError(), task.nextAttemptAt(), task.createdAt(), task.stuck());
    }
}
