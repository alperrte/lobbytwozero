package com.ltz.messaging.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.messaging.domain.entity.AttachmentBlob;

public interface AttachmentBlobRepository extends JpaRepository<AttachmentBlob, UUID> {

    /** İçeriği, artık hiçbir bağlantı göstermiyorsa siler (herkesten silme, MSG-26). */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            delete from attachment_blobs b
            where b.id = :blobId and not exists (select 1 from message_attachments a where a.blob_id = b.id)""",
            nativeQuery = true)
    int deleteIfOrphan(@Param("blobId") UUID blobId);

    /** Temizlik işi: hiçbir bağlantının göstermediği, {@code cutoff}'tan eski içerikler. */
    @Modifying
    @Query(value = """
            delete from attachment_blobs b
            where b.created_at < :cutoff
              and not exists (select 1 from message_attachments a where a.blob_id = b.id)""",
            nativeQuery = true)
    int deleteOrphansBefore(@Param("cutoff") Instant cutoff);
}
