package com.ltz.game.api.dto.request;
import com.ltz.game.domain.enums.FeedbackType;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record FeedbackRequest(@NotNull UUID gameId,@NotNull FeedbackType type,
        @NotBlank @Size(max=120) String title,@NotBlank @Size(max=4000) String body) {
    @Override public String toString() { return "FeedbackRequest[gameId="+gameId+", type="+type+", content=REDACTED]"; }
}
