package com.ltz.auth.application.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ltz.auth.domain.enums.DeviceRevokeReason;
import com.ltz.auth.infrastructure.persistence.repository.DeviceCredentialRepository;

import lombok.RequiredArgsConstructor;

/**
 * Hesap güvenliği değiştiğinde (parola, e-posta, askı ve yasak, silme) hesabın tüm cihaz kimliklerini kapatır. Oturum
 * iptalinin yapıldığı aynı işlemde çağrılır; çağıran transaction'ın parçasıdır. Toplu güncelleme olduğundan
 * persistence context'i temizler, bekleyen değişiklikler önce flush edilir.
 */
@Component
@RequiredArgsConstructor
public class DeviceRevoker {

    private final DeviceCredentialRepository deviceRepository;

    public void revokeAll(UUID accountId, DeviceRevokeReason reason, Instant now) {
        deviceRepository.revokeAllActive(accountId, reason, now);
    }
}
