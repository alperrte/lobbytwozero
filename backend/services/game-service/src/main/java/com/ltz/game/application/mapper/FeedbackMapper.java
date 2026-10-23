package com.ltz.game.application.mapper;
import com.ltz.game.api.dto.response.FeedbackResponse;
import com.ltz.game.domain.entity.GameDeveloperFeedback;
import org.springframework.stereotype.Component;
@Component
public class FeedbackMapper {
    public FeedbackResponse toResponse(GameDeveloperFeedback value) {
        return new FeedbackResponse(value.getId(),value.getGameId(),value.getType(),value.getStatus(),
                value.getTitle(),value.getBody(),value.getVersion(),value.getCreatedAt(),value.getUpdatedAt());
    }
}
