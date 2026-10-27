package com.ltz.auth.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.ltz.auth.domain.entity.SecurityEvent;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID> {

    /** Silme tamamlanınca hesabın tüm güvenlik olayları silinir (silinen kullanıcının kaydı tutulmaz). */
    @Modifying(flushAutomatically = true)
    @Query("delete from SecurityEvent e where e.accountId = :accountId")
    int deleteByAccountId(UUID accountId);
}
