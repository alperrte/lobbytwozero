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
 * {@link GifSnapshot} taşır. Görsel ve GIF mesajında gövde isteğe bağlı açıklamadır. {@code VOICE} (MSG-21) bir
 * {@link MessageAttachment}'a (ses) bağlanır ve gövde taşımaz.
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

    /**
     * Metin mesajında zorunlu; görsel/GIF mesajında isteğe bağlı açıklama ({@code null} olabilir). Herkesten silmede
     * (MSG-26) temizlenir.
     */
    @Column(name = "body")
    private String body;

    /** Yalnız {@code GIF} türünde dolu; herkesten silmede (MSG-26) temizlenir. */
    @Embedded
    private GifSnapshot gif;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Yanıtlanan mesaj (MSG-19), aynı sohbetten. */
    @Column(name = "reply_to_message_id", updatable = false)
    private UUID replyToMessageId;

    /** Kaç kez iletildiği (MSG-20); ilk gönderimde 0. Orijinal gönderen tutulmaz. */
    @Column(name = "forward_count", nullable = false, updatable = false)
    private int forwardCount;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    /** Herkesten silinmiş mi (MSG-26)? Satır kalır (sıra ve okundu bilgisi için), içerik temizlenmiştir. */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Herkesten sil (MSG-26): gövde ve GIF kaydı temizlenir; sıra, gönderen, zaman ve tür kalır. Yetki ve süre
     * uygulama katmanında denetlenir.
     */
    public void deleteForEveryone(UUID actor, Instant now) {
        this.body = null;
        this.gif = null;
        this.deletedBy = Objects.requireNonNull(actor);
        this.deletedAt = Objects.requireNonNull(now);
    }

    /** {@code seq}, sohbet satırı kilitliyken {@link Conversation#appendMessage(Instant)} ile alınmış olmalıdır. */
    public static Message text(UUID conversationId, long seq, UUID senderId, String clientMessageId,
                               UUID replyToMessageId, String body, Instant now) {
        return create(conversationId, seq, senderId, clientMessageId, replyToMessageId, MessageType.TEXT,
                Objects.requireNonNull(body), null, now);
    }

    /** Yüklenmiş görsel; görsel aynı transaction'da bu mesaja bağlanır. {@code caption} {@code null} olabilir. */
    public static Message image(UUID conversationId, long seq, UUID senderId, String clientMessageId,
                                UUID replyToMessageId, String caption, Instant now) {
        return create(conversationId, seq, senderId, clientMessageId, replyToMessageId, MessageType.IMAGE, caption,
                null, now);
    }

    /** Sesli mesaj (MSG-21): açıklama taşımaz; ses aynı transaction'da bu mesaja bağlanır. */
    public static Message voice(UUID conversationId, long seq, UUID senderId, String clientMessageId,
                                UUID replyToMessageId, Instant now) {
        return create(conversationId, seq, senderId, clientMessageId, replyToMessageId, MessageType.VOICE, null, null,
                now);
    }

    /** GIF platformundan seçilmiş GIF. {@code caption} {@code null} olabilir. */
    public static Message gif(UUID conversationId, long seq, UUID senderId, String clientMessageId,
                              UUID replyToMessageId, String caption, GifSnapshot gif, Instant now) {
        return create(conversationId, seq, senderId, clientMessageId, replyToMessageId, MessageType.GIF, caption,
                Objects.requireNonNull(gif), now);
    }

    /**
     * İletilen kopya (MSG-20): tür, gövde ve GIF kaydı kaynaktan; gönderen iletendir, yanıt referansı taşınmaz, iletme
     * sayacı bir artar. Görsel bağlantısı ayrıca (aynı içerikle) açılır.
     */
    public static Message forwardOf(Message source, UUID conversationId, long seq, UUID senderId,
                                    String clientMessageId, Instant now) {
        Message message = create(conversationId, seq, senderId, clientMessageId, null, source.type, source.body,
                source.gif == null ? null : source.gif.copy(), now);
        message.forwardCount = source.forwardCount + 1;
        return message;
    }

    private static Message create(UUID conversationId, long seq, UUID senderId, String clientMessageId,
                                  UUID replyToMessageId, MessageType type, String body, GifSnapshot gif, Instant now) {
        Message message = new Message();
        message.conversationId = conversationId;
        message.seq = seq;
        message.senderId = senderId;
        message.clientMessageId = clientMessageId;
        message.replyToMessageId = replyToMessageId;
        message.type = type;
        message.body = body;
        message.gif = gif;
        message.createdAt = now;
        return message;
    }

    /**
     * Aynı istemci kimliğiyle gelen yeniden gönderim bu mesajın tekrarı mı (aynı sohbet, aynı tür, aynı gövde, aynı
     * yanıt hedefi, GIF'te aynı GIF)? Görselin aynı olup olmadığı ayrıca (bağlı görselden) denetlenir.
     */
    public boolean isRetryOf(UUID otherConversationId, MessageType otherType, String otherBody, UUID otherReplyTo,
                             GifProvider otherGifProvider, String otherGifId) {
        boolean sameGif = type != MessageType.GIF || (gif != null && gif.isSameGif(otherGifProvider, otherGifId));
        return conversationId.equals(otherConversationId) && type == otherType && Objects.equals(body, otherBody)
                && Objects.equals(replyToMessageId, otherReplyTo) && sameGif;
    }
}
