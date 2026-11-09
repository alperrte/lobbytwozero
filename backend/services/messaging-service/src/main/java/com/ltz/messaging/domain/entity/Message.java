package com.ltz.messaging.domain.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.ltz.messaging.domain.enums.GifProvider;
import com.ltz.messaging.domain.enums.MessageType;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Sohbet mesajı (MSG-06, MSG-10). Gönderen ve oluşturulma zamanıyla saklanır; {@code seq} sohbet içindeki kayıpsız
 * sıradır. Gönderen başka servise ait olduğundan yalnız kimlikle (REF) tutulur. {@code (senderId, clientMessageId)}
 * tekildir: aynı istemci kimliğiyle yeniden gönderim yeni mesaj oluşturmaz.
 * <p>
 * Türler (MSG-17/22): {@code TEXT} gövde ister; {@code IMAGE} bir {@link MessageAttachment}'a bağlanır, {@code GIF}
 * {@link GifSnapshot} taşır. Görsel ve GIF mesajında gövde isteğe bağlı açıklamadır.
 */
@Entity
@Table(name = "messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "seq", nullable = false, updatable = false)
    private long seq;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private UUID senderId;

    @Column(name = "client_message_id", nullable = false, updatable = false, length = 64)
    private String clientMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 32)
    private MessageType type;

    /** Metin mesajında zorunlu; görsel/GIF mesajında isteğe bağlı açıklama ({@code null} olabilir). */
    @Column(name = "body", updatable = false)
    private String body;

    /** Yalnız {@code GIF} türünde dolu. */
    @Embedded
    private GifSnapshot gif;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** {@code seq}, sohbet satırı kilitliyken {@link Conversation#appendMessage(Instant)} ile alınmış olmalıdır. */
    public static Message text(UUID conversationId, long seq, UUID senderId, String clientMessageId, String body,
                               Instant now) {
        return create(conversationId, seq, senderId, clientMessageId, MessageType.TEXT, Objects.requireNonNull(body),
                null, now);
    }

    /** Yüklenmiş görsel; görsel aynı transaction'da bu mesaja bağlanır. {@code caption} {@code null} olabilir. */
    public static Message image(UUID conversationId, long seq, UUID senderId, String clientMessageId, String caption,
                                Instant now) {
        return create(conversationId, seq, senderId, clientMessageId, MessageType.IMAGE, caption, null, now);
    }

    /** GIF platformundan seçilmiş GIF. {@code caption} {@code null} olabilir. */
    public static Message gif(UUID conversationId, long seq, UUID senderId, String clientMessageId, String caption,
                              GifSnapshot gif, Instant now) {
        return create(conversationId, seq, senderId, clientMessageId, MessageType.GIF, caption,
                Objects.requireNonNull(gif), now);
    }

    private static Message create(UUID conversationId, long seq, UUID senderId, String clientMessageId,
                                  MessageType type, String body, GifSnapshot gif, Instant now) {
        Message message = new Message();
        message.conversationId = conversationId;
        message.seq = seq;
        message.senderId = senderId;
        message.clientMessageId = clientMessageId;
        message.type = type;
        message.body = body;
        message.gif = gif;
        message.createdAt = now;
        return message;
    }

    /**
     * Aynı istemci kimliğiyle gelen yeniden gönderim bu mesajın tekrarı mı (aynı sohbet, aynı tür, aynı gövde, GIF'te
     * aynı GIF)? Görselin aynı olup olmadığı ayrıca (bağlı görselden) denetlenir.
     */
    public boolean isRetryOf(UUID otherConversationId, MessageType otherType, String otherBody,
                             GifProvider otherGifProvider, String otherGifId) {
        boolean sameGif = type != MessageType.GIF || (gif != null && gif.isSameGif(otherGifProvider, otherGifId));
        return conversationId.equals(otherConversationId) && type == otherType && Objects.equals(body, otherBody)
                && sameGif;
    }
}
