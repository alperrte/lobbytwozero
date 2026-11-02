package com.ltz.messaging.application.service;

import java.util.Collection;
import java.util.UUID;

import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.enums.ReadStatus;

/**
 * Okundu durumu su seviyesi (MSG-11, spec §3.2). Formülün tek yeri: geçmiş, sohbet listesi, gönderim yanıtı ve akış
 * aynı hesabı buradan alır.
 * <pre>W(A) = min over aktif m ≠ A of max(m.last_read_seq, m.visible_from_seq - 1)</pre>
 * {@code visible_from_seq - 1} terimi, mesajdan sonra katılan (ya da yeniden eklenen) üyeyi uygun okuyucu olmaktan
 * çıkarır.
 */
public final class ReadWatermark {

    private ReadWatermark() {
    }

    /**
     * {@code owner}'ın kendi mesajları için su seviyesi. {@code owner} dışında aktif üye yoksa 0'dır (hiçbir mesaj
     * okunmuş sayılmaz).
     *
     * @param activeMembers sohbetin aktif üyeleri ({@code owner} dahil olabilir, yok sayılır)
     */
    public static long of(UUID owner, Collection<ConversationMember> activeMembers) {
        return activeMembers.stream()
                .filter(member -> !member.getUserId().equals(owner))
                .mapToLong(member -> Math.max(member.getLastReadSeq(), member.getVisibleFromSeq() - 1))
                .min()
                .orElse(0L);
    }

    /** {@code seq ≤ watermark} ise mesaj okunmuştur. */
    public static ReadStatus status(long seq, long watermark) {
        return seq <= watermark ? ReadStatus.READ : ReadStatus.SENT;
    }
}
