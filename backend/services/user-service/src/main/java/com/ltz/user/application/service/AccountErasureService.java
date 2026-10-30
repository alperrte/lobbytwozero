package com.ltz.user.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.user.application.port.MediaStorageException;
import com.ltz.user.application.port.MediaStoragePort;
import com.ltz.user.domain.entity.UserMedia;
import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.domain.exception.UserException;
import com.ltz.user.infrastructure.persistence.repository.UserMediaRepository;
import com.ltz.user.infrastructure.persistence.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auth'un hesap silme çağrısı (AUTH-16/17): hesaba ait tüm User verisini kalıcı olarak siler. İdempotenttir: veri
 * yoksa (ya da ikinci çağrıda) hiçbir şey silinmez ve işlem yine başarılıdır; Auth yalnız 2xx'i "silindi" sayar.
 *
 * <p>Sıra: önce hesabın <b>tüm</b> görsel dosyaları (aktif ve silinmeyi bekleyenler dahil) silinir, sonra tek
 * transaction'da profil satırı silinir (görsel üst verisi FK {@code ON DELETE CASCADE} ile gider). Dosya silinemezse
 * {@code 503 ACCOUNT_ERASURE_PENDING} döner ve hiçbir veri satırı silinmez; Auth yeniden dener. Bu yüzden {@code 204},
 * "kişisel veri kalmadı" demektir. {@code inbox_events} hesap kimliği taşımadığından silinecek bir şey içermez.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountErasureService {

    private final UserProfileRepository profiles;
    private final UserMediaRepository media;
    private final MediaStoragePort storage;

    @Transactional
    public void erase(UUID accountId) {
        List<UserMedia> files = media.findByUserId(accountId);
        try {
            for (UserMedia file : files) {
                MediaFiles.delete(storage, file);
            }
        } catch (MediaStorageException _) {
            log.warn("Account erasure postponed: media storage unavailable, accountId={}", accountId);
            throw new UserException(UserErrorCode.ACCOUNT_ERASURE_PENDING);
        }
        int rows = profiles.deleteByUserId(accountId);
        // Yalnız hesap kimliği ve sayılar loglanır; profil içeriği yazılmaz.
        log.info("Account data erased: accountId={}, profiles={}, mediaFiles={}", accountId, rows, files.size());
    }
}
