package com.ltz.mail.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.mail.infrastructure.persistence.repository.MailMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auth → Mail hesap verisi silme (AUTH-16/17, D4): hesaba ait bekleyen/gönderilmiş tüm kuyruk ve geçmiş kayıtları
 * silinir. İdempotenttir: kayıt olmasa da başarılı sayılır. Deneme geçmişi {@code ON DELETE CASCADE} ile birlikte gider;
 * bu sırada gönderilmekte olan bir kaydın sonucu iyimser kilit nedeniyle atılır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountErasureService {

    private final MailMessageRepository messages;

    @Transactional
    public void erase(UUID accountId) {
        int removed = messages.deleteAllForUser(accountId);
        log.info("Account mail data erased accountId={} removedMessages={}", accountId, removed);
    }
}
