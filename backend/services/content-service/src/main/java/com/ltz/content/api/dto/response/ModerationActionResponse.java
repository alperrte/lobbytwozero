package com.ltz.content.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ModerationActionType;
import com.ltz.content.domain.enums.ModerationSource;
import com.ltz.content.domain.enums.ModeratorRole;

/**
 * Moderasyon kaydı (CONT-10). {@code resultingStatus}, kararın içeriği getirdiği durumdur ({@code REMOVE} → {@code REMOVED},
 * {@code RESTORE} → {@code PUBLISHED}). {@code actorId} kararı veren moderatör/yöneticidir, {@code actorRole} admin-service'in doğruladığı rolüdür; yalnız moderasyon uçlarında döner,
 * içeriğin yazarına gösterilmez.
 */
public record ModerationActionResponse(
		UUID id,
		UUID contentId,
		ModerationActionType action,
		ContentStatus resultingStatus,
		UUID actorId,
		ModeratorRole actorRole,
		ModerationSource source,
		String reason,
		UUID reportId,
		Instant createdAt) {
}
