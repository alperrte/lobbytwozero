package com.ltz.game.api.dto.request;

import com.ltz.game.domain.enums.ModerationAction;
import com.ltz.game.domain.enums.ModerationTarget;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code targetId}: MEDIA ve UPDATE_NOTE için zorunlu, DESCRIPTION için gönderilmez. */
public record ModerationRequest(@NotNull ModerationTarget target, UUID targetId, @NotNull ModerationAction action,
        @NotBlank @Size(max=1000) String reason) { }
