package com.ltz.messaging.application.port;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ltz.messaging.application.service.MessageView;
import com.ltz.messaging.application.service.ReadReceiptView;

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

    /** Silinen mesaj olayı (MSG-26). */
    String DELETED_EVENT = "message_deleted";
    /** Tepki olayı (MSG-23). */
    String REACTION_EVENT = "reaction";
    /** Benden sil olayı (MSG-25), yalnız silenin kendi akışlarına. */
    String HIDDEN_EVENT = "message_hidden";

    /**
     * Mesajı alıcıların açık akışlarına {@code message} olayı olarak iletir; her alıcı kendi yükünü alır. Teslim en iyi
     * çabadır, kalıcı değildir.
     */
    void publish(Map<UUID, MessageView> perRecipient);

    /**
     * Mesaj değişikliği olayını ({@link #DELETED_EVENT}, {@link #REACTION_EVENT}, {@link #HIDDEN_EVENT}) alıcıların açık
     * akışlarına iletir. Olay {@code id} taşımaz; kaçıran istemci yeniden bağlanınca açık sohbetin son sayfasını yeniden
     * çeker (ADR 0001).
     */
    void publishEvent(String eventName, Map<UUID, ?> payloadByRecipient);

    /**
     * Okundu bilgisini (MSG-11) alıcının açık akışlarına {@code read} olayı olarak iletir. Yük alıcıya göre
     * kırpılmıştır; teslim en iyi çabadır, kaçan olay geçmişteki {@code readStatus} ile eşitlenir.
     */
    void publishReadReceipt(UUID recipient, ReadReceiptView receipt);
}
