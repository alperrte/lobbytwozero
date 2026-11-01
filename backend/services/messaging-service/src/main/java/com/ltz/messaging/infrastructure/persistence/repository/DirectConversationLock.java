package com.ltz.messaging.infrastructure.persistence.repository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Aynı iki kullanıcı arasındaki birebir sohbetin eşzamanlı açılışını sıraya koyan transaction kapsamlı PostgreSQL
 * advisory kilidi. Böylece ikinci istek, ilkinin oluşturduğu sohbeti bulur; tekil indeks ihlaline düşmez.
 */
@Component
public class DirectConversationLock {

    @PersistenceContext
    private EntityManager entityManager;

    /** Yalnız çalışan bir transaction içinde çağrılabilir; kilit transaction bitince bırakılır. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String directKey) {
        // pg_advisory_xact_lock void döner; Hibernate void sütununu okuyamadığından sayıya sarılır.
        entityManager.createNativeQuery(
                        "select count(*) from (select pg_advisory_xact_lock(hashtextextended(:key, 0))) as locked")
                .setParameter("key", "direct:" + directKey)
                .getSingleResult();
    }
}
