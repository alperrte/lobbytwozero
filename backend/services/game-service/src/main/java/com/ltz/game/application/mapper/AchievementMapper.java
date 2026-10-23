package com.ltz.game.application.mapper;
import com.ltz.game.api.dto.response.GameModuleResponse.Achievement;
import com.ltz.game.domain.entity.GameAchievementDefinition;
import org.springframework.stereotype.Component;
@Component
public class AchievementMapper {
    public Achievement toResponse(GameAchievementDefinition value) {
        return new Achievement(value.getId(),value.getCode(),value.getTitle(),value.getDescription(),
                value.isVisible(),value.getVersion(),"ADMIN",value.getUpdatedAt());
    }
}
