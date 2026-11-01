package com.ltz.messaging.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.messaging.domain.enums.MessageType;

import jakarta.persistence.Column;
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

    @Column(name = "body", nullable = false, updatable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** {@code seq}, sohbet satırı kilitliyken {@link Conversation#appendMessage(Instant)} ile alınmış olmalıdır. */
    public static Message text(UUID conversationId, long seq, UUID senderId, String clientMessageId, String body,
                               Instant now) {
        Message message = new Message();
        message.conversationId = conversationId;
        message.seq = seq;
        message.senderId = senderId;
        message.clientMessageId = clientMessageId;
        message.type = MessageType.TEXT;
        message.body = body;
        message.createdAt = now;
        return message;
    }

    /** Aynı istemci kimliğiyle gelen yeniden gönderim bu mesajın tekrarı mı (aynı sohbet, aynı içerik)? */
    public boolean isRetryOf(UUID otherConversationId, String otherBody) {
        return conversationId.equals(otherConversationId) && body.equals(otherBody);
    }
}
