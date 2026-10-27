package com.ltz.library.api.controller;

import com.ltz.library.application.service.AccountErasureService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Yalnız Auth → servis. Bu yol Gateway'e yönlendirilmez. */
@RestController
public class AccountErasureController {
    public static final String PATH = "/internal/v1/accounts/{accountId}";
    private final AccountErasureService erasure;

    public AccountErasureController(AccountErasureService erasure) { this.erasure = erasure; }

    @DeleteMapping(PATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void erase(@PathVariable UUID accountId, @AuthenticationPrincipal Jwt token) {
        if (!"svc:auth-service".equals(token.getSubject())
                || !accountId.toString().equals(token.getClaims().get("account_id")))
            throw new AccessDeniedException("Servis token'ı bu silme işlemi için geçerli değil.");
        erasure.erase(accountId);
    }
}
