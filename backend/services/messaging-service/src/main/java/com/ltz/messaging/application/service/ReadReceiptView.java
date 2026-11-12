package com.ltz.messaging.application.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Bir alıcıya giden {@code read} akış olayının yükü (MSG-11, spec §5). Değerler alıcının kendi mesajlarıyla kırpılır:
 * ham okuma işaretçisi ve ham su seviyesi taşınmaz, mesaj içeriği yoktur.
 *
 * @param readUpToSeq    alıcının, okuyucunun okuduğu aralıktaki en büyük kendi seq'i
 * @param allReadUpToSeq alıcının su seviyesine kadarki en büyük kendi seq'i; yoksa 0. İstemci kendi mesajlarından
 *                       {@code seq ≤ allReadUpToSeq} olanları {@code READ} yapar
 */
public record ReadReceiptView(UUID conversationId, UUID readerId, Long readUpToSeq, Instant readAt,
                              long allReadUpToSeq) {
}
