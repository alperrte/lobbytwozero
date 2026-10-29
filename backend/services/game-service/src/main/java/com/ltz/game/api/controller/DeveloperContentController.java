package com.ltz.game.api.controller;

import com.ltz.game.api.dto.request.DeveloperDescriptionRequest;
import com.ltz.game.api.dto.request.DeveloperMediaRequest;
import com.ltz.game.api.dto.request.UpdateNoteRequest;
import com.ltz.game.api.dto.response.DeveloperContentManagementResponse;
import com.ltz.game.api.dto.response.ManagedGameResponse;
import com.ltz.game.application.port.Actor;
import com.ltz.game.application.service.DeveloperContentService;
import com.ltz.game.application.service.DeveloperGrantService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Yetkili geliştirici alanı (GAME-12/13). Rol zincirde, oyun bazlı yetki servis katmanında ayrıca denetlenir. */
@RestController @RequestMapping("/api/v1/game/developer") @RequiredArgsConstructor
public class DeveloperContentController {
    private final DeveloperContentService content;
    private final DeveloperGrantService grants;

    @GetMapping("/games")
    public List<ManagedGameResponse> games(Actor actor) { return grants.managedGames(actor); }

    @GetMapping("/games/{gameId}/content")
    public DeveloperContentManagementResponse get(Actor actor, @PathVariable UUID gameId) { return content.management(actor, gameId); }

    @PutMapping("/games/{gameId}/description")
    public DeveloperContentManagementResponse.Description description(Actor actor, @PathVariable UUID gameId,
            @Valid @RequestBody DeveloperDescriptionRequest request) {
        return content.putDescription(actor, gameId, request.description(), request.version());
    }

    @PostMapping("/games/{gameId}/media") @ResponseStatus(HttpStatus.CREATED)
    public DeveloperContentManagementResponse.Media addMedia(Actor actor, @PathVariable UUID gameId, @Valid @RequestBody DeveloperMediaRequest request) {
        return content.addMedia(actor, gameId, request.type(), request.url(), request.altText());
    }

    @DeleteMapping("/games/{gameId}/media/{mediaId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMedia(Actor actor, @PathVariable UUID gameId, @PathVariable UUID mediaId) { content.deleteMedia(actor, gameId, mediaId); }

    @PostMapping("/games/{gameId}/update-notes") @ResponseStatus(HttpStatus.CREATED)
    public DeveloperContentManagementResponse.UpdateNote addNote(Actor actor, @PathVariable UUID gameId, @Valid @RequestBody UpdateNoteRequest request) {
        return content.addNote(actor, gameId, request.title(), request.body());
    }

    @PutMapping("/games/{gameId}/update-notes/{noteId}")
    public DeveloperContentManagementResponse.UpdateNote updateNote(Actor actor, @PathVariable UUID gameId, @PathVariable UUID noteId,
            @Valid @RequestBody UpdateNoteRequest request) {
        return content.updateNote(actor, gameId, noteId, request.title(), request.body(), request.version());
    }

    @DeleteMapping("/games/{gameId}/update-notes/{noteId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNote(Actor actor, @PathVariable UUID gameId, @PathVariable UUID noteId) { content.deleteNote(actor, gameId, noteId); }
}
