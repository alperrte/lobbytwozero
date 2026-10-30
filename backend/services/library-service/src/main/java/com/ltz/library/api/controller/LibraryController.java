package com.ltz.library.api.controller;

import com.ltz.library.api.dto.request.AddLibraryEntryRequest;
import com.ltz.library.api.dto.response.LibraryEntryResponse;
import com.ltz.library.application.service.LibraryService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/library/entries")
public class LibraryController {
    private final LibraryService library;

    public LibraryController(LibraryService library) { this.library = library; }

    @PostMapping
    public LibraryEntryResponse add(@AuthenticationPrincipal Jwt token, @Valid @RequestBody AddLibraryEntryRequest request) {
        return library.add(UUID.fromString(token.getSubject()), request.gameId());
    }

    @DeleteMapping("/{gameId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt token, @PathVariable UUID gameId) {
        library.remove(UUID.fromString(token.getSubject()), gameId);
    }

    @PutMapping("/{gameId}/favorite")
    public LibraryEntryResponse favorite(@AuthenticationPrincipal Jwt token, @PathVariable UUID gameId) {
        return library.favorite(UUID.fromString(token.getSubject()), gameId);
    }

    @PutMapping("/{gameId}/completed")
    public LibraryEntryResponse complete(@AuthenticationPrincipal Jwt token, @PathVariable UUID gameId) {
        return library.complete(UUID.fromString(token.getSubject()), gameId);
    }
}
