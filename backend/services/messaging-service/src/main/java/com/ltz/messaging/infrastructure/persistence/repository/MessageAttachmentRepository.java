package com.ltz.messaging.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.messaging.domain.entity.MessageAttachment;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, UUID> {

    String META = "select new com.ltz.messaging.infrastructure.persistence.repository.AttachmentMeta(a.id, "
            + "a.conversationId, a.uploaderId, a.messageId, a.mimeType, a.sizeBytes, a.width, a.height, a.frameCount, "
            + "a.createdAt) from MessageAttachment a ";

    @Query(META + "where a.id = :id and a.conversationId = :conversationId")
    Optional<AttachmentMeta> findMeta(@Param("id") UUID id, @Param("conversationId") UUID conversationId);

    /** Sayfa/liste için görseller tek sorguda. */
    @Query(META + "where a.messageId in :messageIds")
    List<AttachmentMeta> findMetaByMessageIds(@Param("messageIds") Collection<UUID> messageIds);

    @Query("select a.id from MessageAttachment a where a.messageId = :messageId")
    Optional<UUID> findIdByMessageId(@Param("messageId") UUID messageId);

    Optional<MessageAttachment> findByMessageId(UUID messageId);

    @Query("select a.blobId from MessageAttachment a where a.messageId = :messageId")
    Optional<UUID> findBlobIdByMessageId(@Param("messageId") UUID messageId);

    /** Herkesten silmede (MSG-26) mesajın görsel bağlantısını siler; içerik ayrıca, bağlantısı kalmadıysa silinir. */
    @Modifying(flushAutomatically = true)
    @Query(value = "delete from message_attachments where message_id = :messageId", nativeQuery = true)
    int deleteByMessageId(@Param("messageId") UUID messageId);

    @Query(value = """
            select b.content from message_attachments a join attachment_blobs b on b.id = a.blob_id
            where a.id = :id""", nativeQuery = true)
    Optional<byte[]> findContent(@Param("id") UUID id);

    /** Kullanıcı başına yükleme oranı sınırı; iletmeyle açılan bağlantılar sayılmaz. */
    long countByUploaderIdAndForwardedFalseAndCreatedAtAfter(UUID uploaderId, Instant since);

    /** Kullanıcının henüz mesaja bağlanmamış yüklemeleri. */
    long countByUploaderIdAndMessageIdIsNull(UUID uploaderId);

    /**
     * Görseli mesaja bağlar; yalnız aynı sohbete aynı kullanıcının yüklediği ve henüz bağlanmamış görsel bağlanır. Koşullu
     * tek UPDATE olduğundan eşzamanlı iki gönderim ve temizlik işi aynı görseli paylaşamaz (satır kilidi kazananı seçer).
     *
     * @return bağlanan satır sayısı (0 ya da 1)
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            update message_attachments set message_id = :messageId
            where id = :id and conversation_id = :conversationId and uploader_id = :uploaderId and message_id is null""",
            nativeQuery = true)
    int link(@Param("id") UUID id, @Param("conversationId") UUID conversationId, @Param("uploaderId") UUID uploaderId,
             @Param("messageId") UUID messageId);

    /** Süresi dolmuş, mesaja bağlanmamış yüklemeleri siler. */
    @Modifying
    @Query(value = "delete from message_attachments where message_id is null and created_at < :cutoff",
            nativeQuery = true)
    int deleteUnlinkedBefore(@Param("cutoff") Instant cutoff);
}
