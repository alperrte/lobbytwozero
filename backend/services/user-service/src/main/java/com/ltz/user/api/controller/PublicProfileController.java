package com.ltz.user.api.controller;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.user.api.dto.request.BatchProfilesRequest;
import com.ltz.user.api.dto.response.ProfileCardsResponse;
import com.ltz.user.api.dto.response.PublicProfileResponse;
import com.ltz.user.application.service.PlayerSearchService;
import com.ltz.user.application.service.PublicProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Başkalarının profilleri: açık profil (USER-05, misafire de açık), toplu kart ve oyuncu arama (USER-11; yalnız giriş
 * yapmış üye). İzleyici yalnız doğrulanmış token'dan gelir; token yoksa misafirdir. Yanıt izleyiciye göre değiştiği için
 * önbelleğe alınmaz.
 */
@RestController
@RequiredArgsConstructor
public class PublicProfileController {

    private final PublicProfileService profiles;
    private final PlayerSearchService search;

    @GetMapping(UserApiPaths.PROFILE)
    public ResponseEntity<PublicProfileResponse> byId(@PathVariable UUID userId, @AuthenticationPrincipal Jwt token) {
        return noStore(PublicProfileResponse.from(profiles.byId(userId, viewer(token))));
    }

    @GetMapping(UserApiPaths.PROFILE_BY_USERNAME)
    public ResponseEntity<PublicProfileResponse> byUsername(@PathVariable String username,
                                                            @AuthenticationPrincipal Jwt token) {
        return noStore(PublicProfileResponse.from(profiles.byUsername(username, viewer(token))));
    }

    @PostMapping(UserApiPaths.PROFILES_BATCH)
    public ResponseEntity<ProfileCardsResponse> batch(@AuthenticationPrincipal Jwt token,
                                                      @Valid @RequestBody BatchProfilesRequest request) {
        return noStore(ProfileCardsResponse.from(search.batch(member(token), request.userIds())));
    }

    @GetMapping(UserApiPaths.SEARCH)
    public ResponseEntity<ProfileCardsResponse> search(@AuthenticationPrincipal Jwt token, @RequestParam String q) {
        return noStore(ProfileCardsResponse.from(search.search(member(token), q)));
    }

    /** Misafir için boş; token varsa doğrulayıcı {@code sub}'ın UUID olduğunu zaten garanti eder. */
    private static Optional<UUID> viewer(Jwt token) {
        return Optional.ofNullable(token).map(jwt -> UUID.fromString(jwt.getSubject()));
    }

    /** Arama ve toplu uç güvenlik zincirinde kimlik ister; burada token her zaman vardır. */
    private static UUID member(Jwt token) {
        return UUID.fromString(token.getSubject());
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body);
    }
}
