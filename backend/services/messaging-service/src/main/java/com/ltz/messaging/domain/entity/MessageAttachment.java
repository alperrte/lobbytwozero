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
 * Sohbetteki görsel bağlantısı (MSG-17). İçerik ({@link AttachmentBlob}) sunucuda doğrulanmış ve yeniden
 * kodlanmış/temizlenmiş haldedir (EXIF ve gömülü veri atılmış). Yüklenen görsel önce mesaja bağlanmadan kaydedilir
 * ({@code messageId == null}); mesaj gönderilince tek bir mesaja bağlanır. İletilen görsel (MSG-20) aynı içeriğe hedef
 * sohbette yeni, mesaja bağlı ve {@code forwarded} bir satırla bağlanır; baytlar kopyalanmaz. Bağlama, satırı yüklemeden koşullu bir UPDATE ile yapılır (içerik belleğe alınmaz); bu yüzden
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

    /** Yüklemede boş, iletmede ({@link #forwardCopy}) dolu yazılır; sonraki bağlama koşullu UPDATE'tir. */
    @Column(name = "message_id", updatable = false)
    private UUID messageId;

    @Column(name = "blob_id", nullable = false, updatable = false)
    private UUID blobId;

    @Column(name = "forwarded", nullable = false, updatable = false)
    private boolean forwarded;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static MessageAttachment upload(UUID conversationId, UUID uploaderId, String mimeType, AttachmentBlob blob,
                                           int width, int height, int frameCount, Instant now) {
        MessageAttachment attachment = new MessageAttachment();
        attachment.conversationId = conversationId;
        attachment.uploaderId = uploaderId;
        attachment.mimeType = mimeType;
        attachment.blobId = blob.getId();
        attachment.sizeBytes = blob.getSizeBytes();
        attachment.width = width;
        attachment.height = height;
        attachment.frameCount = frameCount;
        attachment.createdAt = now;
        return attachment;
    }

    /** İletilen görselin hedef sohbetteki bağlantısı (MSG-20): aynı içerik, yeni mesaj, iletenin adına. */
    public static MessageAttachment forwardCopy(UUID conversationId, UUID uploaderId, UUID messageId,
                                                MessageAttachment source, Instant now) {
        MessageAttachment attachment = new MessageAttachment();
        attachment.conversationId = conversationId;
        attachment.uploaderId = uploaderId;
        attachment.messageId = messageId;
        attachment.blobId = source.blobId;
        attachment.forwarded = true;
        attachment.mimeType = source.mimeType;
        attachment.sizeBytes = source.sizeBytes;
        attachment.width = source.width;
        attachment.height = source.height;
        attachment.frameCount = source.frameCount;
        attachment.createdAt = now;
        return attachment;
    }
}
