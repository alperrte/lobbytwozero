package com.ltz.game.application.service;

import com.ltz.game.api.dto.response.DeveloperGrantResponse;
import com.ltz.game.api.dto.response.ManagedGameResponse;
import com.ltz.game.application.port.Actor;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameDeveloperGrant;
import com.ltz.game.domain.enums.ActorRole;
import com.ltz.game.domain.enums.AdminAuditAction;
import com.ltz.game.domain.enums.GameStatus;
import com.ltz.game.domain.exception.ContentNotFoundException;
import com.ltz.game.domain.exception.GameNotFoundException;
import com.ltz.game.infrastructure.persistence.repository.GameDeveloperGrantRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Geliştirici yetkisi yönetimi. Kullanıcının GAME_DEVELOPER rolüne sahip olup olmadığı Auth Service'in konusudur;
 * burada yalnız oyun bazlı yetki saklanır ve kullanım anında token rolüyle birlikte denetlenir.
 */
@Service @RequiredArgsConstructor
public class DeveloperGrantService {
    private final GameRepository games;
    private final GameDeveloperGrantRepository grants;
    private final AdminAuditService audit;
    private final Clock clock;

    @Transactional
    public DeveloperGrantResponse grant(Actor actor, UUID gameId, UUID developerUserId, String reason) {
        ActorRole role = actor.auditRole(ActorRole.ADMIN);
        String text = RequestRules.reason(reason);
        Game game = games.lockGame(gameId).filter(found -> found.getStatus() != GameStatus.MERGED).orElseThrow(GameNotFoundException::new);
        var existing = grants.findByGameIdAndDeveloperUserIdAndRevokedAtIsNull(game.getId(), developerUserId);
        if (existing.isPresent()) return response(existing.get());
        var grant = new GameDeveloperGrant();
        grant.setGameId(game.getId());
        grant.setDeveloperUserId(developerUserId);
        grant.setGrantedBy(actor.userId());
        grant.setGrantedAt(clock.instant());
        grants.saveAndFlush(grant);
        audit.append(actor, role, AdminAuditAction.GRANT_DEVELOPER, game.getId(), text, "developerUserId=" + developerUserId);
        return response(grant);
    }

    @Transactional
    public void revoke(Actor actor, UUID gameId, UUID developerUserId, String reason) {
        ActorRole role = actor.auditRole(ActorRole.ADMIN);
        String text = RequestRules.reason(reason);
        games.lockGame(gameId).orElseThrow(GameNotFoundException::new);
        var grant = grants.findByGameIdAndDeveloperUserIdAndRevokedAtIsNull(gameId, developerUserId)
                .orElseThrow(() -> new ContentNotFoundException("GRANT_NOT_FOUND"));
        grant.setRevokedAt(clock.instant());
        grant.setRevokedBy(actor.userId());
        grants.saveAndFlush(grant);
        audit.append(actor, role, AdminAuditAction.REVOKE_DEVELOPER, gameId, text, "developerUserId=" + developerUserId);
    }

    @Transactional(readOnly = true)
    public List<DeveloperGrantResponse> list(Actor actor, UUID gameId) {
        actor.auditRole(ActorRole.ADMIN);
        games.findById(gameId).orElseThrow(GameNotFoundException::new);
        return grants.findByGameIdAndRevokedAtIsNullOrderByGrantedAtAscIdAsc(gameId).stream().map(DeveloperGrantService::response).toList();
    }

    /** Geliştiricinin kendi yetkili olduğu aktif oyunlar. */
    @Transactional(readOnly = true)
    public List<ManagedGameResponse> managedGames(Actor actor) {
        if (!actor.has(ActorRole.GAME_DEVELOPER)) throw new com.ltz.game.domain.exception.AccessForbiddenException();
        List<UUID> ids = grants.findByDeveloperUserIdAndRevokedAtIsNullOrderByGrantedAtAscIdAsc(actor.userId()).stream()
                .map(GameDeveloperGrant::getGameId).toList();
        if (ids.isEmpty()) return List.of();
        Map<UUID, Game> found = games.findAllById(ids).stream().filter(Game::isActive).collect(Collectors.toMap(Game::getId, Function.identity()));
        return ids.stream().filter(found::containsKey).map(id -> new ManagedGameResponse(id, found.get(id).getTitle())).toList();
    }

    private static DeveloperGrantResponse response(GameDeveloperGrant grant) {
        return new DeveloperGrantResponse(grant.getId(), grant.getGameId(), grant.getDeveloperUserId(), grant.getGrantedAt());
    }
}
