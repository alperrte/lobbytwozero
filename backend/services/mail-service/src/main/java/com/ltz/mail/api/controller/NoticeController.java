package com.ltz.mail.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.mail.api.dto.request.NoticeMailRequest;
import com.ltz.mail.application.mapper.MailRequestMapper;
import com.ltz.mail.application.service.NoticeEnqueueService;
import com.ltz.mail.infrastructure.config.PlatformProperties;
import com.ltz.mail.infrastructure.security.ServiceCallerGuard;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Platform kapısı: Auth dışındaki, izin tablosunda kayıtlı servislerin bilgilendirme maili talebi. Yalnız
 * {@code scope=mail:notify} taşıyan servis token'ıyla ({@code svc+jwt}, {@code sub=svc:<servis>}) çağrılır. Bu uç
 * Notification Service ile ilgili değildir: o servis hangi olayın kime bildirileceğine karar verir, burası yalnızca
 * e-postayı güvenle gönderir. {@code 202} yalnız istek kalıcı olarak kuyruğa yazıldığında döner.
 */
@RestController
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeEnqueueService notices;
    private final PlatformProperties platform;

    @PostMapping(InternalApiPaths.NOTICES)
    public ResponseEntity<Void> notice(@Valid @RequestBody NoticeMailRequest request,
                                       @AuthenticationPrincipal Jwt token) {
        PlatformProperties.Caller limits = ServiceCallerGuard.requirePlatformCaller(token, platform);
        notices.enqueue(ServiceCallerGuard.callerName(token), limits, MailRequestMapper.toCommand(request));
        return ResponseEntity.accepted().build();
    }
}
