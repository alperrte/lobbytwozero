package com.ltz.social.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.social.domain.entity.InviteCode;

import jakarta.persistence.LockModeType;

public interface InviteCodeRepository extends JpaRepository<InviteCode, UUID> {

    /** Kullanıcının etkin (iptal edilmemiş) kodu; süresi dolmuş olsa da iptal edilene kadar etkin sayılır. */
    Optional<InviteCode> findByUserIdAndRevokedAtIsNull(UUID userId);

    /** Hesap silme: kullanıcının tüm davet kodları (iptal edilmiş olanlar dahil). */
    @Modifying
    @Query("delete from InviteCode c where c.userId = :accountId")
    int deleteAllOf(@Param("accountId") UUID accountId);

    /** Kullanım sayacını ve iptali sıraya koymak için satır kilidiyle okur. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from InviteCode c where c.code = :code")
    Optional<InviteCode> findByCodeForUpdate(@Param("code") String code);
}
