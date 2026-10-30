package com.ltz.user.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.user.domain.entity.UserProfile;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    /**
     * Profil yoksa oluşturur; eşzamanlı iki çağrıdan yalnız biri satır ekler (diğeri sessizce atlanır).
     *
     * @return eklenen satır sayısı (0 ya da 1)
     */
    @Modifying
    @Query(value = """
            INSERT INTO user_profiles (user_id, account_status, status_changed_at, created_at, updated_at, version)
            VALUES (:userId, :status, :statusChangedAt, :now, :now, 0)
            ON CONFLICT (user_id) DO NOTHING""", nativeQuery = true)
    int insertIfAbsent(@Param("userId") UUID userId, @Param("status") String status,
            @Param("statusChangedAt") Instant statusChangedAt, @Param("now") Instant now);

    Optional<UserProfile> findByUsernameNormalized(String usernameNormalized);

    /**
     * Oyuncu arama (USER-11): kullanıcı adı ya da görünen ad önekiyle, yalnız {@code ACTIVE} hesaplar (askıdaki hesap
     * aramada çıkmaz, K6) ve izleyiciyle herhangi bir yönde engeli olmayanlar. {@code prefix} LIKE özel karakterleri
     * kaçırılmış ve {@code %} ile bitmiş olarak gelir. Kısmi {@code text_pattern_ops} indeksleri kullanılır.
     */
    @Query(value = """
            SELECT p.* FROM user_profiles p
            WHERE p.account_status = 'ACTIVE'
              AND (p.username_normalized LIKE :prefix ESCAPE '\\' OR p.display_name_normalized LIKE :prefix ESCAPE '\\')
              AND NOT EXISTS (SELECT 1 FROM social_blocks b
                              WHERE b.active AND ((b.blocker_id = :viewer AND b.blocked_id = p.user_id)
                                               OR (b.blocker_id = p.user_id AND b.blocked_id = :viewer)))
            ORDER BY p.username_normalized NULLS LAST, p.display_name_normalized, p.user_id
            LIMIT :limit""", nativeQuery = true)
    List<UserProfile> search(@Param("prefix") String prefix, @Param("viewer") UUID viewer, @Param("limit") int limit);

    /** Hesap silme: profil satırı; profil görselleri üst verisi FK ile birlikte silinir. */
    @Modifying
    @Query("delete from UserProfile p where p.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
