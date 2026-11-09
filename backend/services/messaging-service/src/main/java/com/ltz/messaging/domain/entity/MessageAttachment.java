package com.ltz.messaging.domain.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Sohbete yüklenmiş görsel (MSG-17). İçerik sunucuda doğrulanmış ve yeniden kodlanmış/temizlenmiş haldedir (EXIF ve gömülü
 * veri atılmış). Önce mesaja bağlanmadan kaydedilir ({@code messageId == null}); mesaj gönderilince tek bir mesaja
 * bağlanır. Bağlama, satırı yüklemeden koşullu bir UPDATE ile yapılır (içerik belleğe alınmaz); bu yüzden
 * {@code messageId} entity üzerinden değişmez. Listeler içeriği değil yalnız ölçüleri okur
 * ({@code AttachmentMeta}).
 */
@Entity
@Table(name = "message_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageAttachment {

    /** Yüklenen her dosyanın azami boyutu: 1 MB (MSG-17). Şemadaki CHECK ile aynı değer. */
    public static final int MAX_SIZE_BYTES = 1_048_576;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "uploader_id", nullable = false, updatable = false)
    private UUID uploaderId;

    @Column(name = "message_id", insertable = false, updatable = false)
    private UUID messageId;

    @Column(name = "mime_type", nullable = false, updatable = false, length = 32)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private int sizeBytes;

    @Column(name = "width", nullable = false, updatable = false)
    private int width;

    @Column(name = "height", nullable = false, updatable = false)
    private int height;

    @Column(name = "frame_count", nullable = false, updatable = false)
    private int frameCount;

    /** İçerik yalnız indirme ucunda ayrı sorguyla okunur; iç dizi dışarı verilmez. */
    @Getter(AccessLevel.NONE)
    @Column(name = "content", nullable = false, updatable = false)
    private byte[] content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static MessageAttachment upload(UUID conversationId, UUID uploaderId, String mimeType, byte[] content,
                                           int width, int height, int frameCount, Instant now) {
        if (content.length == 0 || content.length > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Attachment content size is out of range.");
        }
        MessageAttachment attachment = new MessageAttachment();
        attachment.conversationId = conversationId;
        attachment.uploaderId = uploaderId;
        attachment.mimeType = mimeType;
        attachment.content = content.clone();
        attachment.sizeBytes = content.length;
        attachment.width = width;
        attachment.height = height;
        attachment.frameCount = frameCount;
        attachment.createdAt = now;
        return attachment;
    }
}
