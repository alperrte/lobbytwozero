package com.ltz.game.application.service;

import com.ltz.game.application.port.Actor;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.enums.ActorRole;
import com.ltz.game.domain.exception.AccessForbiddenException;
import com.ltz.game.domain.exception.GameNotFoundException;
import com.ltz.game.infrastructure.persistence.repository.GameDeveloperGrantRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * GAME-12: Auth rolü (token) ve oyun bazlı yetki (game_developer_grants) ayrı ayrı denetlenir; ikisi birlikte gerekir.
 * Yetki yoksa oyunun durumu hakkında ek bilgi verilmez.
 */
@Component @RequiredArgsConstructor
public class GameAccessPolicy {
    private final GameRepository games;
    private final GameDeveloperGrantRepository grants;

    /** Mutasyonlar için oyun satırını kilitler; aynı oyunun içerik sınırları ardışık değerlendirilir. */
    public Game requireDeveloper(Actor actor, UUID gameId, boolean forUpdate) {
        if (!actor.has(ActorRole.GAME_DEVELOPER) || !grants.existsByGameIdAndDeveloperUserIdAndRevokedAtIsNull(gameId, actor.userId()))
            throw new AccessForbiddenException();
        var game = forUpdate ? games.lockGame(gameId) : games.findById(gameId);
        // Yetki iptali de aynı oyun kilidini alır. READ COMMITTED altında kilit
        // beklerken commit edilen iptali burada yeniden okuruz; kilit transaction
        // sonuna kadar tutulduğu için kontrolden sonra iptal araya giremez.
        if (forUpdate && !grants.existsByGameIdAndDeveloperUserIdAndRevokedAtIsNull(gameId, actor.userId()))
            throw new AccessForbiddenException();
        return game.filter(Game::isActive).orElseThrow(GameNotFoundException::new);
    }
}
