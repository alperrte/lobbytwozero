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
 * Sohbetteki görsel (MSG-17) ya da sesli mesaj (MSG-21) bağlantısı. Görselde ölçüler, seste süre ve isteğe bağlı dalga
 * formu doludur (DB CHECK {@code ck_message_attachments_kind}). İçerik ({@link AttachmentBlob}) sunucuda doğrulanmış ve yeniden
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

    /** Sesli mesajın saklanan türü: sunucu her sesi WebM olarak yeniden paketler (MSG-21). */
    public static final String VOICE_MIME_TYPE = "audio/webm";

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

    /** Yalnız görselde dolu. */
    @Column(name = "width", updatable = false)
    private Integer width;

    @Column(name = "height", updatable = false)
    private Integer height;

    @Column(name = "frame_count", updatable = false)
    private Integer frameCount;

    /** Yalnız seste dolu: paketlerden hesaplanan çalınma süresi. */
    @Column(name = "duration_ms", updatable = false)
    private Integer durationMs;

    /** Yalnız seste, isteğe bağlı: 64 değerlik dalga formu (0–100). */
    @Getter(AccessLevel.NONE)
    @Column(name = "waveform", updatable = false)
    private byte[] waveform;

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

    /** Yeniden paketlenmiş sesli mesaj (MSG-21): her zaman {@code audio/webm}. {@code waveform} {@code null} olabilir. */
    public static MessageAttachment uploadVoice(UUID conversationId, UUID uploaderId, AttachmentBlob blob,
                                                int durationMs, byte[] waveform, Instant now) {
        MessageAttachment attachment = new MessageAttachment();
        attachment.conversationId = conversationId;
        attachment.uploaderId = uploaderId;
        attachment.mimeType = VOICE_MIME_TYPE;
        attachment.blobId = blob.getId();
        attachment.sizeBytes = blob.getSizeBytes();
        attachment.durationMs = durationMs;
        attachment.waveform = waveform == null ? null : waveform.clone();
        attachment.createdAt = now;
        return attachment;
    }

    /** Ses mi (görsel değil)? */
    public boolean isVoice() {
        return VOICE_MIME_TYPE.equals(mimeType);
    }

    public byte[] getWaveform() {
        return waveform == null ? null : waveform.clone();
    }

    /** İletilen görselin ya da sesin hedef sohbetteki bağlantısı (MSG-20): aynı içerik, yeni mesaj, iletenin adına. */
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
        attachment.durationMs = source.durationMs;
        attachment.waveform = source.waveform == null ? null : source.waveform.clone();
        attachment.createdAt = now;
        return attachment;
    }
}
