package com.ltz.user.api.controller;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.user.api.dto.request.UpdateConsentsRequest;
import com.ltz.user.api.dto.response.ConsentsResponse;
import com.ltz.user.application.service.ConsentsService;
import com.ltz.user.application.service.UpdateConsentsCommand;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Kendi rızalarım (LIB-14). Kimlik yalnız token'dan gelir; yanıtlar kişisel olduğundan önbelleğe alınmaz. */
@RestController
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentsService consents;

    @GetMapping(UserApiPaths.ME_CONSENTS)
    public ResponseEntity<ConsentsResponse> consents(@AuthenticationPrincipal Jwt token) {
        return noStore(ConsentsResponse.from(consents.get(userId(token))));
    }

    @PutMapping(UserApiPaths.ME_CONSENTS)
    public ResponseEntity<ConsentsResponse> updateConsents(@AuthenticationPrincipal Jwt token,
                                                           @Valid @RequestBody UpdateConsentsRequest request) {
        UpdateConsentsCommand command = new UpdateConsentsCommand(request.version(), request.recommendation());
        return noStore(ConsentsResponse.from(consents.update(userId(token), command)));
    }

    private static UUID userId(Jwt token) {
        return UUID.fromString(token.getSubject());
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
