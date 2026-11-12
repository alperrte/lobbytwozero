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
 * Görselin doğrulanmış içeriği (MSG-17). Birden çok mesaj bağlantısı ({@link MessageAttachment}) aynı içeriği
 * paylaşabilir: iletilen görsel baytları kopyalamaz (MSG-20). Bağlı son bağlantı da kalkınca içerik silinir.
 */
@Entity
@Table(name = "attachment_blobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttachmentBlob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private int sizeBytes;

    /** İçerik yalnız indirme ucunda ayrı sorguyla okunur; iç dizi dışarı verilmez. */
    @Getter(AccessLevel.NONE)
    @Column(name = "content", nullable = false, updatable = false)
    private byte[] content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AttachmentBlob store(byte[] content, Instant now) {
        if (content.length == 0 || content.length > MessageAttachment.MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Attachment content size is out of range.");
        }
        AttachmentBlob blob = new AttachmentBlob();
        blob.content = content.clone();
        blob.sizeBytes = content.length;
        blob.createdAt = now;
        return blob;
    }
}
