package com.ltz.auth.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.auth.api.dto.request.ConfirmEmailVerificationRequest;
import com.ltz.auth.api.dto.request.ResendEmailVerificationRequest;
import com.ltz.auth.api.dto.response.VerificationPendingResponse;
import com.ltz.auth.application.service.EmailVerificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @PostMapping(AuthApiPaths.EMAIL_VERIFICATION_CONFIRM)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@Valid @RequestBody ConfirmEmailVerificationRequest request) {
        emailVerificationService.confirm(request.email(), request.code());
    }

    @PostMapping(AuthApiPaths.EMAIL_VERIFICATION_RESEND)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VerificationPendingResponse resend(@Valid @RequestBody ResendEmailVerificationRequest request) {
        emailVerificationService.resend(request.email());
        return VerificationPendingResponse.VERIFY_EMAIL;
    }
}
