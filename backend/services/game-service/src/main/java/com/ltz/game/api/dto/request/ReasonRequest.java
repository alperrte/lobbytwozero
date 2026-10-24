package com.ltz.game.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Yönetim işlemlerinde zorunlu gerekçe (denetim kaydına yazılır). */
public record ReasonRequest(@NotBlank @Size(max=1000) String reason) { }
