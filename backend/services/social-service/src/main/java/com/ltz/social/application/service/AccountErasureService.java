package com.ltz.social.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.social.infrastructure.persistence.repository.FriendRequestRepository;
import com.ltz.social.infrastructure.persistence.repository.FriendshipRepository;
import com.ltz.social.infrastructure.persistence.repository.InviteCodeRepository;
import com.ltz.social.infrastructure.persistence.repository.OutboxEventRepository;
import com.ltz.social.infrastructure.persistence.repository.UserBlockRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auth'un hesap silme çağrısı (AUTH-16/17): hesaba ait tüm Social verisini kalıcı olarak siler. İdempotenttir:
 * veri yoksa (ya da ikinci çağrıda) hiçbir şey silinmez ve işlem yine başarılıdır; Auth yalnız 2xx'i "silindi"
 * sayar. Tek transaction'da yapılır; kısmi silme olmaz. Başka kullanıcıların verisi (ör. arkadaşın kendi listesi)
 * değişmez; yalnız silinen hesapla ilgili kayıtlar kalkar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountErasureService {

    private final FriendRequestRepository friendRequests;
    private final FriendshipRepository friendships;
    private final UserBlockRepository blocks;
    private final InviteCodeRepository inviteCodes;
    private final OutboxEventRepository outbox;

    @Transactional
    public void erase(UUID accountId) {
        // Olay yükleri kimlik taşıdığından önce silinir; istek/arkadaşlık/engel kayıtlarından bağımsızdır.
        int events = outbox.deleteReferencing(accountId.toString());
        int requests = friendRequests.deleteAllOf(accountId);
        int friends = friendships.deleteAllOf(accountId);
        int blockRows = blocks.deleteAllOf(accountId);
        int codes = inviteCodes.deleteAllOf(accountId);
        // Yalnız hesap kimliği ve sayılar loglanır; içerik/ilişki ayrıntısı yazılmaz.
        log.info("Account data erased: accountId={}, requests={}, friendships={}, blocks={}, inviteCodes={}, events={}",
                accountId, requests, friends, blockRows, codes, events);
    }
}
