package com.ltz.messaging.application.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ltz.messaging.application.port.MessageStreamPort;

import lombok.RequiredArgsConstructor;

/**
 * MSG-09 gerçek zamanlı akış. Akış, kullanıcının o an aktif üyesi olduğu sohbetlerdeki yeni mesajları taşır (alıcılar
 * gönderim anında üyelikten belirlenir; çıkarılan üye yeni mesaj almaz). Bağlantı token'ın bitişinde kapanır; istemci
 * yeni token'la yeniden bağlanır ve kaçırdığını geçmiş ucundan çeker.
 */
@Service
@RequiredArgsConstructor
public class MessageStreamService {

    private final MessageStreamPort streams;

    public SseEmitter open(UUID userId, Instant tokenExpiresAt) {
        return streams.open(userId, tokenExpiresAt);
    }
}
