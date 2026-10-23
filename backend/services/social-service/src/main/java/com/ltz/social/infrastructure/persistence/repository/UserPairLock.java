package com.ltz.social.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.social.domain.entity.FriendshipId;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * İki kullanıcı arasındaki ilişki değişikliklerini (istek gönderme, engelleme) sıraya koyan transaction kapsamlı
 * PostgreSQL advisory kilidi. Kilit çift için sırasızdır ((A,B) ve (B,A) aynı kilit) ve transaction bitince
 * kendiliğinden bırakılır. Böylece "engel konurken aynı anda istek gönderme" yarışında bekleyen istek kalamaz.
 */
@Component
public class UserPairLock {

    @PersistenceContext
    private EntityManager entityManager;

    /** Yalnız çalışan bir transaction içinde çağrılabilir; aksi halde kilit hemen bırakılırdı. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(UUID first, UUID second) {
        FriendshipId pair = FriendshipId.of(first, second);
        // pg_advisory_xact_lock void döner; Hibernate void sütununu okuyamadığından sayıya sarılır.
        entityManager.createNativeQuery(
                        "select count(*) from (select pg_advisory_xact_lock(hashtextextended(:pair, 0))) as locked")
                .setParameter("pair", pair.getUserLowId() + ":" + pair.getUserHighId())
                .getSingleResult();
    }
}
