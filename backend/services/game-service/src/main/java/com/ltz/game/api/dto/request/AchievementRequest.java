package com.ltz.game.api.dto.request;
import jakarta.validation.constraints.*;
public record AchievementRequest(@NotBlank @Size(max=120) String title,
        @Size(max=2000) String description, @NotNull Boolean visible,
        @PositiveOrZero Long version, @NotBlank @Size(min=8,max=1000) String reason) {
    public com.ltz.game.application.service.AchievementEdit toEdit() {
        return new com.ltz.game.application.service.AchievementEdit(title,description,visible,version,reason);
    }
}
