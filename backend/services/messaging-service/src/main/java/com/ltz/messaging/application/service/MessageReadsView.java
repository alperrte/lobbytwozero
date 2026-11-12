package com.ltz.messaging.application.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.messaging.domain.enums.ReadStatus;

/**
 * Mesajı kimin ne zaman okuduğu (MSG-12). {@code readers} okuma saatine göre yeniden eskiye, {@code pending} katılma
 * sırasına göredir. Her iki liste de yalnız uygun okuyucuları içerir (spec §3.1).
 *
 * @param readStatus sohbet geçmişiyle aynı su seviyesi formülünden gelir; listelerden türetilmez
 */
public record MessageReadsView(UUID messageId, long seq, ReadStatus readStatus, List<Reader> readers,
                               List<UUID> pending) {

    /** @param readAt okuma saati; migration öncesi okumalarda {@code null} */
    public record Reader(UUID userId, Instant readAt) {
    }
}
