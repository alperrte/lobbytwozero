package com.ltz.messaging.application.port;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ltz.messaging.application.service.MessageView;

/**
 * Gerçek zamanlı iletim (MSG-09, docs/decisions/0001-gercek-zamanli-mesajlasma.md). Akış yalnız bildirim taşır; mesajın
 * kalıcı kaydı DB'dedir. Kopan ya da bildirim kaçıran istemci, geçmiş ucundan {@code after=<son seq>} ile eşitlenir.
 */
public interface MessageStreamPort {

    /**
     * Kullanıcı için yeni bir akış açar.
     *
     * @param closeBy bu andan sonra akış kapatılır (token bitişi); istemci yeni token'la yeniden bağlanır
     * @throws com.ltz.messaging.domain.exception.MessagingException 429 kullanıcının akış sınırı doluysa
     */
    SseEmitter open(UUID userId, Instant closeBy);

    /** Mesajı alıcıların açık akışlarına iletir; teslim en iyi çabadır, kalıcı değildir. */
    void publish(Collection<UUID> recipients, MessageView message);
}
