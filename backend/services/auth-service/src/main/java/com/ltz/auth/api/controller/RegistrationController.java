package com.ltz.auth.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.auth.api.dto.request.RegisterAccountRequest;
import com.ltz.auth.api.dto.response.VerificationPendingResponse;
import com.ltz.auth.application.service.RegistrationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping(AuthApiPaths.REGISTER)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VerificationPendingResponse register(@Valid @RequestBody RegisterAccountRequest request) {
        registrationService.register(request.email(), request.username(), request.password());
        return VerificationPendingResponse.VERIFY_EMAIL;
    }
}
