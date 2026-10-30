package com.ltz.user.api.controller;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.user.api.dto.request.UpdateLinkedAccountRequest;
import com.ltz.user.api.dto.response.LinkedAccountsResponse;
import com.ltz.user.application.service.LinkedAccountService;
import com.ltz.user.application.service.LinkedAccountView;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Kendi bağlı hesaplarım (USER-07/08). Bağlama ve kaldırma Auth'tadır; burada durum okunur ve açık profilde gösterme izni
 * yönetilir. Kimlik yalnız token'dan gelir; yanıtlar kişisel olduğundan önbelleğe alınmaz.
 */
@RestController
@RequiredArgsConstructor
public class LinkedAccountController {

    private final LinkedAccountService linkedAccounts;

    @GetMapping(UserApiPaths.ME_LINKED_ACCOUNTS)
    public ResponseEntity<LinkedAccountsResponse> list(@AuthenticationPrincipal Jwt token) {
        return noStore(new LinkedAccountsResponse(linkedAccounts.list(userId(token))));
    }

    @PatchMapping(UserApiPaths.ME_LINKED_ACCOUNT)
    public ResponseEntity<LinkedAccountView> updateVisibility(@AuthenticationPrincipal Jwt token,
                                                              @PathVariable String provider,
                                                              @Valid @RequestBody UpdateLinkedAccountRequest request) {
        return noStore(linkedAccounts.updateVisibility(userId(token), provider, request.showOnProfile(),
                request.version()));
    }

    private static UUID userId(Jwt token) {
        return UUID.fromString(token.getSubject());
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
