package com.ltz.auth.application.service;

import java.time.Instant;
import java.util.UUID;

/** Silme koordinasyonunda tamamlanmamış görevin uygulama katmanı görünümü. Kişisel veri yoktur. */
public record PendingDeletionTask(UUID requestId, UUID accountId, String service, int attempts, String lastError,
                                  Instant nextAttemptAt, Instant createdAt, boolean stuck) {
}
