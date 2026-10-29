package com.ltz.game.application.service;
import com.ltz.game.api.dto.response.GameModuleResponse;
import com.ltz.game.application.mapper.AchievementMapper;
import com.ltz.game.application.port.Actor;
import com.ltz.game.domain.entity.GameAchievementDefinition;
import com.ltz.game.domain.enums.ActorRole;
import com.ltz.game.domain.enums.AdminAuditAction;
import com.ltz.game.domain.enums.GameModuleType;
import com.ltz.game.domain.exception.AccessForbiddenException;
import com.ltz.game.domain.exception.GameConflictException;
import com.ltz.game.domain.exception.GameNotFoundException;
import com.ltz.game.domain.exception.InvalidGameRequestException;
import com.ltz.game.infrastructure.persistence.repository.GameAchievementRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class GameModuleService {
    private final GameRepository games;
    private final GameAchievementRepository achievements;
    private final AchievementMapper mapper;
    private final AdminAuditService audit;
    private final Clock clock;

    @Transactional(readOnly=true)
    public GameModuleResponse read(UUID gameId,GameModuleType type,int page,int size) {
        var paging=paging(page,size);
        games.findById(gameId).filter(com.ltz.game.domain.entity.Game::isActive).orElseThrow(GameNotFoundException::new);
        if(type!=GameModuleType.ACHIEVEMENTS)
            return new GameModuleResponse(gameId,type,GameModuleResponse.State.INTEGRATION_PENDING,List.of(),page,size,null);
        return response(gameId,achievements.findByGameIdAndVisibleTrue(gameId,paging),page,size);
    }
    @Transactional(readOnly=true)
    public GameModuleResponse management(Actor actor,UUID gameId,int page,int size) {
        requireAdmin(actor);
        games.findById(gameId).orElseThrow(GameNotFoundException::new);
        return response(gameId,achievements.findByGameId(gameId,paging(page,size)),page,size);
    }
    @Transactional
    public GameModuleResponse.Achievement put(Actor actor,UUID gameId,String code,AchievementEdit edit) {
        requireAdmin(actor);
        if(code==null || !RequestRules.TERM_CODE.matcher(code).matches()) throw new InvalidGameRequestException("INVALID_ACHIEVEMENT_CODE");
        String validTitle=RequestRules.text(edit.title(),120,"INVALID_ACHIEVEMENT");
        String validDescription=RequestRules.optionalText(edit.description(),2000,"INVALID_ACHIEVEMENT");
        String evidence=RequestRules.reason(edit.reason());
        games.lockGame(gameId).filter(com.ltz.game.domain.entity.Game::isActive).orElseThrow(GameNotFoundException::new);
        var value=achievements.findByGameIdAndCode(gameId,code).orElse(null);
        if(value==null) {
            if(edit.version()!=null) throw new GameConflictException("VERSION_CONFLICT");
            if(achievements.countByGameId(gameId)>=1000) throw new GameConflictException("ACHIEVEMENT_LIMIT_REACHED");
            value=new GameAchievementDefinition(); value.setGameId(gameId); value.setCode(code); value.setCreatedAt(clock.instant());
        } else if(edit.version()==null || edit.version()!=value.getVersion()) throw new GameConflictException("VERSION_CONFLICT");
        value.setTitle(validTitle); value.setDescription(validDescription); value.setVisible(edit.visible()); value.setUpdatedAt(clock.instant());
        value=achievements.saveAndFlush(value);
        audit.append(actor,ActorRole.ADMIN,AdminAuditAction.UPDATE_GAME,gameId,evidence,"achievement="+value.getId());
        return mapper.toResponse(value);
    }
    private GameModuleResponse response(UUID id,Page<GameAchievementDefinition> values,int page,int size) {
        return new GameModuleResponse(id,GameModuleType.ACHIEVEMENTS,
                values.getTotalElements()==0?GameModuleResponse.State.EMPTY:GameModuleResponse.State.AVAILABLE,
                values.stream().map(mapper::toResponse).toList(),page,size,values.getTotalElements());
    }
    static PageRequest paging(int page,int size) {
        if(page<0 || page>10000 || size<1 || size>50) throw new InvalidGameRequestException("INVALID_PAGINATION");
        return PageRequest.of(page,size,Sort.by("code").ascending().and(Sort.by("id")));
    }
    private void requireAdmin(Actor actor) {
        if(actor==null || !actor.has(ActorRole.ADMIN)) throw new AccessForbiddenException();
    }
}
