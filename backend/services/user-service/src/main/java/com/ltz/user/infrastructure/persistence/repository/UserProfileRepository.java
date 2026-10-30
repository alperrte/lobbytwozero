package com.ltz.user.infrastructure.persistence.repository;

import java.time.Instant;
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

    /** Hesap silme: profil satırı; profil görselleri üst verisi FK ile birlikte silinir. */
    @Modifying
    @Query("delete from UserProfile p where p.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
