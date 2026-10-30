package com.ltz.user.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.user.domain.entity.UserMedia;
import com.ltz.user.domain.enums.MediaKind;
import com.ltz.user.domain.enums.MediaStatus;

public interface UserMediaRepository extends JpaRepository<UserMedia, UUID> {

    List<UserMedia> findByUserIdAndStatus(UUID userId, MediaStatus status);

    List<UserMedia> findByUserIdAndKindAndStatus(UUID userId, MediaKind kind, MediaStatus status);

    List<UserMedia> findByUserId(UUID userId);

    /** Yükleme sınırı: kullanıcının son pencerede yaptığı yüklemeler (değiştirilenler dahil). */
    @Query("select count(m) from UserMedia m where m.userId = :userId and m.createdAt >= :since")
    long countUploadsSince(@Param("userId") UUID userId, @Param("since") Instant since);

    /** Temizleyici: dosyası silinecek satırlar, en eskiden başlayarak. */
    List<UserMedia> findByStatusOrderByCreatedAtAsc(MediaStatus status, Limit limit);

    /** Yükleme sınırı penceresi dolan ve dosyası silinmiş satırları kaldırır. */
    @Modifying
    @Query("delete from UserMedia m where m.id in :ids")
    int deleteAllByIds(@Param("ids") List<UUID> ids);
}
