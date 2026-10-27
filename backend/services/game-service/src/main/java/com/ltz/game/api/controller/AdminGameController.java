package com.ltz.game.api.controller;

import com.ltz.game.api.dto.request.*;
import com.ltz.game.api.dto.response.AdminGameResponse;
import com.ltz.game.api.dto.response.DeveloperGrantResponse;
import com.ltz.game.application.port.Actor;
import com.ltz.game.application.service.*;
import com.ltz.game.domain.enums.OverridableField;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Yetkili oyun yönetimi (GAME-07/12). Rol kontrolü güvenlik zincirinde, aktör kimliği merkezi çözümleyiciden gelir. */
@RestController @RequestMapping("/api/v1/game/admin") @RequiredArgsConstructor
public class AdminGameController {
    private final AdminGameService games;
    private final AdminEditorialService editorial;
    private final DeveloperGrantService grants;

    @PostMapping("/games") @ResponseStatus(HttpStatus.CREATED)
    public AdminGameResponse create(Actor actor, @Valid @RequestBody CreateGameRequest request) { return games.create(actor, request.toEdit()); }

    @GetMapping("/games/{gameId}")
    public AdminGameResponse get(Actor actor, @PathVariable UUID gameId) { return games.get(actor, gameId); }

    @PatchMapping("/games/{gameId}")
    public AdminGameResponse update(Actor actor, @PathVariable UUID gameId, @Valid @RequestBody UpdateGameRequest request) {
        return games.update(actor, gameId, request.version(), request.toEdit());
    }

    @PostMapping("/games/{gameId}/archive")
    public AdminGameResponse archive(Actor actor, @PathVariable UUID gameId, @Valid @RequestBody ReasonRequest request) {
        return games.archive(actor, gameId, request.reason());
    }

    @PostMapping("/games/{gameId}/restore")
    public AdminGameResponse restore(Actor actor, @PathVariable UUID gameId, @Valid @RequestBody ReasonRequest request) {
        return games.restore(actor, gameId, request.reason());
    }

    @PostMapping("/games/{gameId}/manual-overrides/{field}/release")
    public AdminGameResponse releaseOverride(Actor actor, @PathVariable UUID gameId, @PathVariable OverridableField field,
            @Valid @RequestBody ReasonRequest request) {
        return games.releaseOverride(actor, gameId, field, request.reason());
    }

    @PutMapping("/popular") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replacePopular(Actor actor, @Valid @RequestBody EditorialSelectionRequest request) {
        editorial.replace(actor, request.gameIds(), request.reason());
    }

    @PostMapping("/games/{gameId}/developers")
    public DeveloperGrantResponse grant(Actor actor, @PathVariable UUID gameId, @Valid @RequestBody GrantDeveloperRequest request) {
        return grants.grant(actor, gameId, request.developerUserId(), request.reason());
    }

    @GetMapping("/games/{gameId}/developers")
    public List<DeveloperGrantResponse> developers(Actor actor, @PathVariable UUID gameId) { return grants.list(actor, gameId); }

    @PostMapping("/games/{gameId}/developers/{developerUserId}/revoke") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(Actor actor, @PathVariable UUID gameId, @PathVariable UUID developerUserId, @Valid @RequestBody ReasonRequest request) {
        grants.revoke(actor, gameId, developerUserId, request.reason());
    }
}
