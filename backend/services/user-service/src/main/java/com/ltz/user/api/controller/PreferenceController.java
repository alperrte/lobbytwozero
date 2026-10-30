package com.ltz.user.api.controller;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.user.api.dto.request.CompleteOnboardingRequest;
import com.ltz.user.api.dto.request.UpdatePreferencesRequest;
import com.ltz.user.api.dto.response.OnboardingResponse;
import com.ltz.user.api.dto.response.PreferencesResponse;
import com.ltz.user.application.service.CompleteOnboardingCommand;
import com.ltz.user.application.service.OnboardingService;
import com.ltz.user.application.service.PreferencesService;
import com.ltz.user.application.service.UpdatePreferencesCommand;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Kendi tercihlerim (USER-10) ve ilk kurulum akışı (USER-09). Kimlik yalnız token'dan gelir; yanıtlar kişisel olduğundan
 * önbelleğe alınmaz.
 */
@RestController
@RequiredArgsConstructor
public class PreferenceController {

    private final PreferencesService preferences;
    private final OnboardingService onboarding;

    @GetMapping(UserApiPaths.ME_PREFERENCES)
    public ResponseEntity<PreferencesResponse> preferences(@AuthenticationPrincipal Jwt token) {
        return noStore(PreferencesResponse.from(preferences.get(userId(token))));
    }

    @PutMapping(UserApiPaths.ME_PREFERENCES)
    public ResponseEntity<PreferencesResponse> updatePreferences(@AuthenticationPrincipal Jwt token,
                                                                 @Valid @RequestBody UpdatePreferencesRequest request) {
        UpdatePreferencesCommand command = new UpdatePreferencesCommand(request.version(), request.locale(),
                request.theme(), request.reducedMotion(), request.compactLayout());
        return noStore(PreferencesResponse.from(preferences.update(userId(token), command)));
    }

    @GetMapping(UserApiPaths.ME_ONBOARDING)
    public ResponseEntity<OnboardingResponse> onboarding(@AuthenticationPrincipal Jwt token) {
        return noStore(OnboardingResponse.from(onboarding.status(userId(token))));
    }

    @PostMapping(UserApiPaths.ME_ONBOARDING_COMPLETE)
    public ResponseEntity<OnboardingResponse> completeOnboarding(@AuthenticationPrincipal Jwt token,
                                                                 @Valid @RequestBody CompleteOnboardingRequest request) {
        CompleteOnboardingCommand command = new CompleteOnboardingCommand(request.version(), request.playerTypes(),
                request.favoriteGenres(), request.favoritePlatforms(), request.locale(), request.theme());
        return noStore(OnboardingResponse.from(onboarding.complete(userId(token), command)));
    }

    private static UUID userId(Jwt token) {
        return UUID.fromString(token.getSubject());
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
