package com.ltz.messaging.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.messaging.application.port.ImageInspectorPort;
import com.ltz.messaging.application.port.ImageInspectorPort.ProcessedImage;
import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.entity.AttachmentBlob;
import com.ltz.messaging.domain.entity.HiddenMessageId;
import com.ltz.messaging.domain.entity.MessageAttachment;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.ltz.messaging.infrastructure.config.AttachmentProperties;
import com.ltz.messaging.infrastructure.persistence.repository.AttachmentMeta;
import com.ltz.messaging.infrastructure.persistence.repository.AttachmentBlobRepository;
import com.ltz.messaging.infrastructure.persistence.repository.HiddenMessageRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageAttachmentRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageRepository;

import lombok.RequiredArgsConstructor;

/**
 * Sohbete görsel yükleme ve indirme (MSG-17). Ayrı medya servisi yoktur; görsel messaging_db'de saklanır.
 * <p>
 * <b>Yükleme:</b> aktif üye → oran ve bekleyen yükleme sınırı → görsel transaction dışında doğrulanıp temizlenir
 * ({@link ImageInspectorPort}; CPU işi DB bağlantısı tutmaz) → üyelik ve sınırlar yeniden denetlenip kaydedilir. Görsel
 * henüz bir mesaja bağlı değildir; mesaj gönderilince bağlanır, {@code pendingTtl} içinde bağlanmazsa silinir.
 * <p>
 * <b>İndirme:</b> aktif üye; mesaja bağlı görseli yalnız o mesajı görebilen (katıldıktan sonraki mesaj) üye, bağlanmamış
 * görseli yalnız yükleyen indirir. Diğer her durum (başka sohbetin görseli dahil) aynı {@code 404}'tür.
 */
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    private final ConversationAccessPolicy access;
    private final MessageAttachmentRepository attachments;
    private final AttachmentBlobRepository blobs;
    private final HiddenMessageRepository hidden;
    private final MessageRepository messages;
    private final ImageInspectorPort inspector;
    private final AttachmentProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public AttachmentView upload(UUID uploader, UUID conversationId, byte[] content, String contentType) {
        transactions.executeWithoutResult(status -> requireCanUpload(uploader, conversationId));
        ProcessedImage image = inspector.process(content, contentType);
        return Objects.requireNonNull(transactions.execute(status -> {
            requireCanUpload(uploader, conversationId);
            Instant now = now();
            AttachmentBlob blob = blobs.save(AttachmentBlob.store(image.content(), now));
            MessageAttachment saved = attachments.save(MessageAttachment.upload(conversationId, uploader,
                    image.mimeType(), blob, image.width(), image.height(), image.frames(), now));
            return AttachmentView.of(saved.getId(), conversationId, saved.getMimeType(), saved.getSizeBytes(),
                    saved.getWidth(), saved.getHeight(), saved.getFrameCount());
        }));
    }

    @Transactional(readOnly = true)
    public AttachmentContent download(UUID viewer, UUID conversationId, UUID attachmentId) {
        ConversationMember member = access.requireMember(conversationId, viewer);
        AttachmentMeta meta = attachments.findMeta(attachmentId, conversationId)
                .filter(found -> canSee(member, found))
                .orElseThrow(() -> new MessagingException(MessagingErrorCode.ATTACHMENT_NOT_FOUND));
        byte[] content = attachments.findContent(meta.id())
                .orElseThrow(() -> new MessagingException(MessagingErrorCode.ATTACHMENT_NOT_FOUND));
        return new AttachmentContent(meta.mimeType(), content);
    }

    /**
     * Süresi dolmuş, mesaja bağlanmamış yüklemeleri ve artık hiçbir bağlantının göstermediği içerikleri siler; silinen
     * bağlantı sayısını döner. İçerik için de aynı süre beklenir: yeni yüklenmiş ya da bağlanmakta olan içerik silinmez.
     */
    @Transactional
    public int deleteExpiredUploads() {
        Instant cutoff = now().minus(properties.pendingTtl());
        int deleted = attachments.deleteUnlinkedBefore(cutoff);
        blobs.deleteOrphansBefore(cutoff);
        return deleted;
    }

    private boolean canSee(ConversationMember member, AttachmentMeta meta) {
        if (meta.messageId() == null) {
            return meta.uploaderId().equals(member.getUserId());
        }
        return messages.findById(meta.messageId())
                .map(message -> member.canSee(message.getSeq())
                        && !hidden.existsById(new HiddenMessageId(member.getUserId(), message.getId())))
                .orElse(false);
    }

    private void requireCanUpload(UUID uploader, UUID conversationId) {
        access.requireMember(conversationId, uploader);
        if (attachments.countByUploaderIdAndForwardedFalseAndCreatedAtAfter(uploader, now().minus(RATE_WINDOW))
                >= properties.maxPerMinute()) {
            throw new MessagingException(MessagingErrorCode.TOO_MANY_UPLOADS);
        }
        if (attachments.countByUploaderIdAndMessageIdIsNull(uploader) >= properties.maxPending()) {
            throw new MessagingException(MessagingErrorCode.TOO_MANY_PENDING_ATTACHMENTS);
        }
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
