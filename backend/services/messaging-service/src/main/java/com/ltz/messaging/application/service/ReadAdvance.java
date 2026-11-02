package com.ltz.messaging.application.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Okuma işaretçisinin gerçekleşmiş bir ilerlemesi (MSG-11): {@code readerId} bu sohbette {@code [fromSeq, toSeq]}
 * aralığını {@code readAt} anında okudu. Aynı ilerleme için bir okuma aralığı satırı yazılmıştır.
 *
 * @param visibleFromSeq okuyucunun görebildiği ilk seq; aralığın bundan önceki kısmı okundu bilgisi üretmez
 */
public record ReadAdvance(UUID conversationId, UUID readerId, long fromSeq, long toSeq, Instant readAt,
                          long visibleFromSeq) {
}
