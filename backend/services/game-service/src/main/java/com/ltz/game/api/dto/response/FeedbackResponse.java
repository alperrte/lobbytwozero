package com.ltz.game.api.dto.response;
import com.ltz.game.domain.enums.FeedbackStatus;
import com.ltz.game.domain.enums.FeedbackType;
import java.time.Instant;
import java.util.UUID;
/** Yazar kimliği ve profil bilgisi gereksiz yere geliştiriciye açılmaz. */
public record FeedbackResponse(UUID id,UUID gameId,FeedbackType type,FeedbackStatus status,
        String title,String body,long version,Instant createdAt,Instant updatedAt) {
    @Override public String toString() { return "FeedbackResponse[id="+id+", gameId="+gameId+", status="+status+", content=REDACTED]"; }
}
