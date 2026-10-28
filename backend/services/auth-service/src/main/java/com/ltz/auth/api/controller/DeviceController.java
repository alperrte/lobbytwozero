package com.ltz.auth.api.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.auth.api.dto.request.BindDeviceRequest;
import com.ltz.auth.api.dto.request.DeviceTokenRequest;
import com.ltz.auth.api.dto.response.BoundDeviceResponse;
import com.ltz.auth.api.dto.response.DeviceResponse;
import com.ltz.auth.api.dto.response.DeviceTokenResponse;
import com.ltz.auth.application.service.DeviceCredentialService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Masaüstü cihaz kimliği. Bağlama, listeleme ve iptal kullanıcı oturumuyla (cookie + CSRF); cihaz token'ı ise cihaz
 * anahtarıyla, çerezsiz ve herkese açık uçtan alınır (kimlik yalnız gövdedeki anahtardır).
 */
@RestController
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceCredentialService deviceService;

    /** Gizli anahtar yalnız bu yanıtta bir kez döner. */
    @PostMapping(AuthApiPaths.DEVICES)
    public ResponseEntity<BoundDeviceResponse> bind(@AuthenticationPrincipal Jwt jwt,
                                                    @Valid @RequestBody BindDeviceRequest request) {
        BoundDeviceResponse body = BoundDeviceResponse.from(deviceService.bind(UUID.fromString(jwt.getSubject()),
                request.label(), request.password(), request.code(), request.recoveryCode()));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(body);
    }

    @GetMapping(AuthApiPaths.DEVICES)
    public ResponseEntity<List<DeviceResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        List<DeviceResponse> body = deviceService.list(UUID.fromString(jwt.getSubject())).stream()
                .map(DeviceResponse::from).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    @DeleteMapping(AuthApiPaths.DEVICE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID deviceId) {
        deviceService.revoke(UUID.fromString(jwt.getSubject()), deviceId);
    }

    @PostMapping(AuthApiPaths.DEVICES_TOKEN)
    public ResponseEntity<DeviceTokenResponse> token(@Valid @RequestBody DeviceTokenRequest request) {
        DeviceTokenResponse body = DeviceTokenResponse.from(
                deviceService.issueToken(request.deviceId(), request.deviceSecret()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
