package com.ltz.game.application.service;
import com.ltz.game.api.dto.response.FeedbackPageResponse;
import com.ltz.game.api.dto.response.FeedbackResponse;
import com.ltz.game.application.mapper.FeedbackMapper;
import com.ltz.game.application.port.Actor;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameDeveloperFeedback;
import com.ltz.game.domain.enums.ActorRole;
import com.ltz.game.domain.enums.AdminAuditAction;
import com.ltz.game.domain.enums.FeedbackStatus;
import com.ltz.game.domain.enums.FeedbackType;
import com.ltz.game.domain.exception.AccessForbiddenException;
import com.ltz.game.domain.exception.ContentNotFoundException;
import com.ltz.game.domain.exception.GameConflictException;
import com.ltz.game.domain.exception.GameNotFoundException;
import com.ltz.game.domain.exception.InvalidGameRequestException;
import com.ltz.game.infrastructure.persistence.repository.GameFeedbackRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class GameFeedbackService {
    private final GameRepository games;
    private final GameFeedbackRepository feedback;
    private final GameAccessPolicy access;
    private final FeedbackMapper mapper;
    private final AdminAuditService audit;
    private final Clock clock;

    @Transactional
    public FeedbackResponse create(Actor actor,UUID gameId,FeedbackType type,String title,String body) {
        requireActor(actor);
        String validTitle=RequestRules.text(title,120,"INVALID_FEEDBACK");
        String validBody=RequestRules.text(body,4000,"INVALID_FEEDBACK");
        if(type==null) throw new InvalidGameRequestException("INVALID_FEEDBACK_TYPE");
        var game=games.lockGame(gameId).filter(Game::isActive).orElseThrow(GameNotFoundException::new);
        if(type==FeedbackType.EARLY_ACCESS && !Boolean.TRUE.equals(game.getEarlyAccess()))
            throw new InvalidGameRequestException("GAME_NOT_EARLY_ACCESS");
        var value=new GameDeveloperFeedback(); value.setGameId(gameId); value.setAuthorId(actor.userId());
        value.setType(type); value.setTitle(validTitle); value.setBody(validBody);
        value.setCreatedAt(clock.instant()); value.setUpdatedAt(value.getCreatedAt());
        return mapper.toResponse(feedback.saveAndFlush(value));
    }
    @Transactional(readOnly=true)
    public FeedbackResponse own(Actor actor,UUID id) {
        requireActor(actor);
        return mapper.toResponse(feedback.findByIdAndAuthorId(id,actor.userId()).orElseThrow(GameFeedbackService::notFound));
    }
    @Transactional(readOnly=true)
    public FeedbackPageResponse ownList(Actor actor,UUID gameId,FeedbackType type,FeedbackStatus status,int page,int size) {
        requireActor(actor);
        return page(feedback.own(actor.userId(),gameId,type,status,paging(page,size)),page,size);
    }
    @Transactional(readOnly=true)
    public FeedbackPageResponse developerList(Actor actor,UUID gameId,FeedbackType type,FeedbackStatus status,int page,int size) {
        access.requireDeveloper(actor,gameId,false);
        return page(feedback.forGame(gameId,type,status,paging(page,size)),page,size);
    }
    @Transactional(readOnly=true)
    public FeedbackResponse developer(Actor actor,UUID gameId,UUID id) {
        access.requireDeveloper(actor,gameId,false);
        return mapper.toResponse(feedback.findByIdAndGameId(id,gameId).orElseThrow(GameFeedbackService::notFound));
    }
    @Transactional
    public FeedbackResponse transition(Actor actor,UUID gameId,UUID id,FeedbackStatus target,Long version) {
        access.requireDeveloper(actor,gameId,true);
        var value=feedback.findByIdAndGameId(id,gameId).orElseThrow(GameFeedbackService::notFound);
        if(version==null || version!=value.getVersion()) throw new GameConflictException("VERSION_CONFLICT");
        FeedbackStatus from=value.getStatus();
        if(!((from==FeedbackStatus.OPEN && target==FeedbackStatus.IN_PROGRESS)
                || (from==FeedbackStatus.IN_PROGRESS && target==FeedbackStatus.RESOLVED)))
            throw new GameConflictException("INVALID_FEEDBACK_TRANSITION");
        value.setStatus(target); value.setUpdatedAt(clock.instant()); feedback.saveAndFlush(value);
        audit.append(actor,ActorRole.GAME_DEVELOPER,AdminAuditAction.UPDATE_GAME,gameId,null,
                "feedback="+id+";from="+from+";to="+target);
        return mapper.toResponse(value);
    }
    private FeedbackPageResponse page(Page<GameDeveloperFeedback> values,int page,int size) {
        return new FeedbackPageResponse(values.stream().map(mapper::toResponse).toList(),page,size,values.getTotalElements());
    }
    private PageRequest paging(int page,int size) {
        if(page<0 || page>10000 || size<1 || size>50) throw new InvalidGameRequestException("INVALID_PAGINATION");
        return PageRequest.of(page,size,Sort.by("createdAt").descending().and(Sort.by("id")));
    }
    private void requireActor(Actor actor) { if(actor==null) throw new AccessForbiddenException(); }
    private static ContentNotFoundException notFound() { return new ContentNotFoundException("FEEDBACK_NOT_FOUND"); }
}
