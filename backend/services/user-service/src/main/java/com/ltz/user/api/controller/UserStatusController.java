package com.ltz.user.api.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.user.api.dto.request.UserStatusRequest;
import com.ltz.user.api.dto.response.UserStatusResponse;
import com.ltz.user.application.service.UserStatusService;
import com.ltz.user.infrastructure.security.TokenClaims;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Hesap kullanılabilirliği (iç uç, kullanıcıya açık değil). Yalnız {@code scope=users:status:read} taşıyan ve çağıranı
 * izinli listede (messaging, social) olan servis token'ıyla çağrılır; aksi halde {@code 403}. Kullanıcı token'ı
 * {@code 401}. Uç hesabın var olup olmadığını sızdırabileceği için kullanıcı token'ı kabul edilmez ve yanıt yalnız
 * {@code ACTIVE | UNAVAILABLE} verir.
 */
@RestController
@RequiredArgsConstructor
public class UserStatusController {

    private final UserStatusService statuses;

    @PostMapping(InternalApiPaths.USER_STATUS)
    public ResponseEntity<UserStatusResponse> status(@Valid @RequestBody UserStatusRequest request,
                                                     @AuthenticationPrincipal Jwt token) {
        if (!TokenClaims.STATUS_READ_SUBJECTS.contains(token.getSubject())) {
            throw new AccessDeniedException("Service is not allowed to read account availability");
        }
        var results = statuses.lookup(request.userIds()).stream()
                .map(status -> new UserStatusResponse.Item(status.userId(), status.value())).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new UserStatusResponse(results));
    }
}
