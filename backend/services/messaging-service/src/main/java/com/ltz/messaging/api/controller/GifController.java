package com.ltz.messaging.api.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.messaging.api.dto.response.GifSearchResponse;
import com.ltz.messaging.application.service.GifService;

import lombok.RequiredArgsConstructor;

/** GIF platformunda arama ve öne çıkanlar (MSG-22). Kimlik ister; platform anahtarı istemciye hiç gitmez. */
@RestController
@RequiredArgsConstructor
public class GifController {

    private final GifService gifs;

    @GetMapping(MessagingApiPaths.GIF_SEARCH)
    public ResponseEntity<GifSearchResponse> search(@AuthenticationPrincipal Jwt jwt,
                                                    @RequestParam(name = "q", required = false) String query,
                                                    @RequestParam(required = false) Integer limit,
                                                    @RequestParam(required = false) Integer offset,
                                                    @RequestParam(name = "lang", required = false) String language) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(GifSearchResponse.from(gifs.search(AuthenticatedUser.id(jwt), query, limit, offset, language)));
    }

    @GetMapping(MessagingApiPaths.GIF_TRENDING)
    public ResponseEntity<GifSearchResponse> trending(@AuthenticationPrincipal Jwt jwt,
                                                      @RequestParam(required = false) Integer limit,
                                                      @RequestParam(required = false) Integer offset) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(GifSearchResponse.from(gifs.trending(AuthenticatedUser.id(jwt), limit, offset)));
    }
}
