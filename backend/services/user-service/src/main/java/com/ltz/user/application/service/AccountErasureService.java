package com.ltz.user.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.user.infrastructure.persistence.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auth'un hesap silme çağrısı (AUTH-16/17): hesaba ait tüm User verisini kalıcı olarak siler. İdempotenttir: veri
 * yoksa (ya da ikinci çağrıda) hiçbir şey silinmez ve işlem yine başarılıdır; Auth yalnız 2xx'i "silindi" sayar. Tek
 * transaction'da yapılır; kısmi silme olmaz. Profil görselleri üst verisi profil satırıyla birlikte silinir (FK
 * {@code ON DELETE CASCADE}); görsel dosyalarının silinmesi Faz 1b'de bu servise eklenir. {@code inbox_events}
 * hesap kimliği taşımadığından silinecek bir şey içermez.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountErasureService {

    private final UserProfileRepository profiles;

    @Transactional
    public void erase(UUID accountId) {
        int rows = profiles.deleteByUserId(accountId);
        // Yalnız hesap kimliği ve sayı loglanır; profil içeriği yazılmaz.
        log.info("Account data erased: accountId={}, profiles={}", accountId, rows);
    }
}
