package com.ltz.lfp.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Bir sahibin ilan oluşturma isteklerini sıraya koyan transaction kapsamlı PostgreSQL advisory kilidi. Kilit
 * transaction bitince kendiliğinden bırakılır; böylece aynı anda gönderilen istekler açık ilan ve saatlik sınırı
 * birlikte aşamaz. Anahtar {@code lfp:<ownerId>} biçimindedir (başka servislerin kilitleriyle karışmaz; her servisin
 * veritabanı da ayrıdır).
 */
@Component
public class OwnerLock {

    @PersistenceContext
    private EntityManager entityManager;

    /** Yalnız çalışan bir transaction içinde çağrılabilir; aksi halde kilit hemen bırakılırdı. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(UUID ownerId) {
        // pg_advisory_xact_lock void döner; Hibernate void sütununu okuyamadığından sayıya sarılır.
        entityManager.createNativeQuery(
                        "select count(*) from (select pg_advisory_xact_lock(hashtextextended(:key, 0))) as locked")
                .setParameter("key", "lfp:" + ownerId)
                .getSingleResult();
    }
}
