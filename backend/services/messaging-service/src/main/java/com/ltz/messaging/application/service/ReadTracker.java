package com.ltz.messaging.application.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.entity.MessageReadRange;
import com.ltz.messaging.infrastructure.persistence.repository.MessageReadRangeRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

/**
 * Okuma işaretçisini ilerletmenin tek giriş noktası (MSG-11, MSG-13): {@code markRead} ve mesaj gönderimi (gönderen
 * kendi mesajına kadar okumuş sayılır) buradan geçer. İşaretçi gerçekten ilerlediğinde, eski işaretçi + 1'den yeni
 * işaretçiye kadar tek bir okuma aralığı yazılır; aynı seq'in tekrarı, geri gitme ve son mesajın ötesi satır yazmaz.
 * Her gerçek ilerleme commit sonrası {@code read} akış olayını da tetikler ({@link ReadReceiptService}). {@code join}
 * ve {@code rejoin}'in işaretçiyi taşıması okuma değildir, bu sınıftan geçmez.
 */
@Component
public class ReadTracker {

    private final MessageReadRangeRepository ranges;
    private final ReadReceiptService readReceipts;

    @PersistenceContext
    private EntityManager entityManager;

    public ReadTracker(MessageReadRangeRepository ranges, ReadReceiptService readReceipts) {
        this.ranges = ranges;
        this.readReceipts = readReceipts;
    }

    /**
     * Üyenin işaretçisini {@code targetSeq}'e ilerletir. Üye satırı önce kilitlenip yeniden okunur: eşzamanlı okumalar
     * sıraya girer ve aralığın başı güncel işaretçiden alınır, böylece aralıklar çakışmaz ve boşluk bırakmaz. Kilit
     * PostgreSQL'de {@code FOR NO KEY UPDATE}'dir; {@code FOR UPDATE} olsaydı {@code message_read_ranges} yabancı anahtar
     * eklemesinin aldığı {@code KEY SHARE} ile çakışıp kilitlenmeye yol açardı. Kilitli {@code refresh} şarttır;
     * gönderim yolunda üye daha önce kilitsiz yüklenmiştir. Çağıranın transaction'ına katılır.
     *
     * @param targetSeq sohbetin son mesajını aşmamalıdır (çağıran sınırlar)
     * @return işaretçi ilerlediyse yazılan aralık; ilerlemediyse ya da üye bu arada ayrıldıysa boş
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ReadAdvance> advance(ConversationMember member, long targetSeq, Instant now) {
        entityManager.refresh(member, LockModeType.PESSIMISTIC_WRITE);
        long previous = member.getLastReadSeq();
        if (!member.isActive() || targetSeq <= previous) {
            return Optional.empty();
        }
        member.markRead(targetSeq);
        UUID conversationId = member.getId().getConversationId();
        ranges.save(MessageReadRange.of(conversationId, member.getUserId(), previous + 1, targetSeq, now));
        ReadAdvance advance = new ReadAdvance(conversationId, member.getUserId(), previous + 1, targetSeq, now,
                member.getVisibleFromSeq());
        readReceipts.onReadAdvanced(advance);
        return Optional.of(advance);
    }
}
