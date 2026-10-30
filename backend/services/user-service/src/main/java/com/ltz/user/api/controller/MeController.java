package com.ltz.user.api.controller;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.user.api.dto.request.UpdateProfileRequest;
import com.ltz.user.api.dto.response.ProfileResponse;
import com.ltz.user.application.service.ProfileService;
import com.ltz.user.application.service.UpdateProfileCommand;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Oyuncunun kendi profili (USER-01/02). {@code userId} yalnız doğrulanmış token'ın {@code sub} değerinden alınır;
 * istekte kimlik alanı yoktur. Yanıtlar kişisel olduğundan önbelleğe alınmaz.
 */
@RestController
@RequiredArgsConstructor
public class MeController {

    private final ProfileService profiles;

    @GetMapping(UserApiPaths.ME)
    public ResponseEntity<ProfileResponse> me(@AuthenticationPrincipal Jwt token) {
        return noStore(ProfileResponse.from(profiles.getOrProvision(userId(token))));
    }

    @PatchMapping(UserApiPaths.ME_PROFILE)
    public ResponseEntity<ProfileResponse> updateProfile(@AuthenticationPrincipal Jwt token,
                                                         @Valid @RequestBody UpdateProfileRequest request) {
        UpdateProfileCommand command = new UpdateProfileCommand(request.version(), request.displayName(),
                request.bio(), request.country(), request.playerTypes(), request.favoriteGenres(),
                request.favoritePlatforms());
        return noStore(ProfileResponse.from(profiles.update(userId(token), command)));
    }

    /** Token doğrulayıcısı {@code sub}'ın UUID olduğunu zaten garanti eder. */
    private static UUID userId(Jwt token) {
        return UUID.fromString(token.getSubject());
    }

    private static ResponseEntity<ProfileResponse> noStore(ProfileResponse body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
