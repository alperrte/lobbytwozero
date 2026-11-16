package com.ltz.user.api.controller;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.user.api.dto.response.AdminProfileSearchResponse;
import com.ltz.user.application.service.AdminProfileSearchService;

import lombok.RequiredArgsConstructor;

/** Yönetim paneli profil araması. Yetki {@code SecurityConfig}'te (yalnız ADMIN); yanıt önbelleğe alınmaz. */
@RestController
@RequiredArgsConstructor
public class AdminProfileController {

    private final AdminProfileSearchService search;

    @GetMapping(UserApiPaths.ADMIN_PROFILES_SEARCH)
    public ResponseEntity<AdminProfileSearchResponse> search(@AuthenticationPrincipal Jwt token,
                                                             @RequestParam String q) {
        AdminProfileSearchResponse body = AdminProfileSearchResponse.from(
                search.search(UUID.fromString(token.getSubject()), q));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body);
    }
}
