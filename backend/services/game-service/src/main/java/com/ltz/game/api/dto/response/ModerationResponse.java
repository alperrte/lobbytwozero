package com.ltz.game.api.dto.response;

import com.ltz.game.domain.enums.ContentStatus;
import com.ltz.game.domain.enums.ModerationTarget;
import java.util.UUID;

public record ModerationResponse(ModerationTarget target, UUID targetId, ContentStatus status) { }
