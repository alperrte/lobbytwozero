package com.ltz.mail.api.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.mail.api.dto.request.AccountDeletionMailRequest;
import com.ltz.mail.api.dto.request.PasswordResetMailRequest;
import com.ltz.mail.api.dto.request.SecurityNotificationMailRequest;
import com.ltz.mail.api.dto.request.VerificationCodeMailRequest;
import com.ltz.mail.application.mapper.MailRequestMapper;
import com.ltz.mail.application.service.MailCommand;
import com.ltz.mail.application.service.MailEnqueueService;
import com.ltz.mail.infrastructure.security.ServiceCallerGuard;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Auth → Mail iç uçları (MAIL-01..04). Yalnız {@code scope=mail:send} taşıyan, Auth'un imzaladığı servis token'ıyla
 * ({@code svc+jwt}) çağrılır; token'ın {@code account_id} claim'i gövdedeki {@code accountId} ile aynı olmalıdır.
 * {@code 202} yalnız istek kalıcı olarak kuyruğa yazıldığında döner; e-postanın gönderildiği anlamına gelmez.
 */
@RestController
@RequiredArgsConstructor
public class MailDeliveryController {

    private final MailEnqueueService enqueue;

    @PostMapping(InternalApiPaths.VERIFICATION_CODES)
    public ResponseEntity<Void> verificationCode(@Valid @RequestBody VerificationCodeMailRequest request,
                                                 @AuthenticationPrincipal Jwt token) {
        return accept(request.accountId(), token, MailRequestMapper.toCommand(request));
    }

    @PostMapping(InternalApiPaths.PASSWORD_RESETS)
    public ResponseEntity<Void> passwordReset(@Valid @RequestBody PasswordResetMailRequest request,
                                              @AuthenticationPrincipal Jwt token) {
        return accept(request.accountId(), token, MailRequestMapper.toCommand(request));
    }

    @PostMapping(InternalApiPaths.ACCOUNT_DELETIONS)
    public ResponseEntity<Void> accountDeletion(@Valid @RequestBody AccountDeletionMailRequest request,
                                                @AuthenticationPrincipal Jwt token) {
        return accept(request.accountId(), token, MailRequestMapper.toCommand(request));
    }

    @PostMapping(InternalApiPaths.SECURITY_NOTIFICATIONS)
    public ResponseEntity<Void> securityNotification(@Valid @RequestBody SecurityNotificationMailRequest request,
                                                     @AuthenticationPrincipal Jwt token) {
        return accept(request.accountId(), token, MailRequestMapper.toCommand(request));
    }

    private ResponseEntity<Void> accept(UUID accountId, Jwt token, MailCommand command) {
        ServiceCallerGuard.requireAuthForAccount(token, accountId);
        enqueue.enqueue(command);
        return ResponseEntity.accepted().build();
    }
}
