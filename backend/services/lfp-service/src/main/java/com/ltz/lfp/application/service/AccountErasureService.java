package com.ltz.lfp.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.lfp.infrastructure.persistence.repository.ListingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auth'un hesap silme çağrısı (AUTH-16/17, LFP-18): hesabın tüm ilanlarını (her durumda; rolleri FK cascade ile)
 * kalıcı olarak siler. LFP başka kullanıcının verisinde bu hesaba referans tutmaz (mesajlaşma messaging-service'te,
 * engeller social-service'tedir), bu yüzden anonimleştirilecek başka satır yoktur. İdempotenttir: veri yoksa ya da
 * ikinci çağrıda hiçbir şey silinmez ve işlem yine başarılıdır. Tek transaction; kısmi silme olmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountErasureService {

    private final ListingRepository listings;

    @Transactional
    public void erase(UUID accountId) {
        int deleted = listings.deleteAllOwnedBy(accountId);
        // Yalnız hesap kimliği ve sayı loglanır; ilan içeriği yazılmaz.
        log.info("Account data erased: accountId={}, listings={}", accountId, deleted);
    }
}
