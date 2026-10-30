package com.ltz.library.api.controller;

import com.ltz.library.api.dto.request.AddLibraryEntryRequest;
import com.ltz.library.api.dto.response.LibraryEntryResponse;
import com.ltz.library.api.dto.response.LibraryEntryPageResponse;
import com.ltz.library.application.service.LibraryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @DeleteMapping("/{gameId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfavorite(@AuthenticationPrincipal Jwt token, @PathVariable UUID gameId) {
        library.unfavorite(UUID.fromString(token.getSubject()), gameId);
    }

    @GetMapping("/favorites")
    public LibraryEntryPageResponse favorites(@AuthenticationPrincipal Jwt token,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return library.favorites(UUID.fromString(token.getSubject()), page, size);
    }

    @DeleteMapping("/{gameId}/completed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uncomplete(@AuthenticationPrincipal Jwt token, @PathVariable UUID gameId) {
        library.uncomplete(UUID.fromString(token.getSubject()), gameId);
    }

    @GetMapping("/completed")
    public LibraryEntryPageResponse completed(@AuthenticationPrincipal Jwt token,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return library.completed(UUID.fromString(token.getSubject()), page, size);
    }
}
