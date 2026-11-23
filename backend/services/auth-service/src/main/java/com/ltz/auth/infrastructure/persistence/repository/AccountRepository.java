package com.ltz.auth.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.enums.AccountStatus;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    boolean existsByUsernameNormalized(String usernameNormalized);

    boolean existsByEmailNormalized(String emailNormalized);

    Optional<Account> findByEmailNormalized(String emailNormalized);

    /** Aynı hesap için eşzamanlı kod üretimini sıraya sokar (yeniden gönderme yarışı). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.emailNormalized = :emailNormalized")
    Optional<Account> findByEmailNormalizedForUpdate(String emailNormalized);

    @EntityGraph(attributePaths = "roles")
    Optional<Account> findWithRolesByEmailNormalized(String emailNormalized);

    @EntityGraph(attributePaths = "roles")
    Optional<Account> findWithRolesByUsernameNormalized(String usernameNormalized);

    @EntityGraph(attributePaths = "roles")
    Optional<Account> findWithRolesById(UUID id);

    /** Süresi dolan bekleyen hesaplar; SKIP LOCKED ile eşzamanlı temizlikler birbirini beklemez/çakışmaz. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select a from Account a where a.status = :status and a.createdAt < :cutoff order by a.createdAt")
    List<Account> findStaleForUpdate(AccountStatus status, Instant cutoff, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(UUID id);

    /** Askı süresi dolan hesaplar; SKIP LOCKED ile eşzamanlı örnekler aynı hesabı iki kez açmaz. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select a from Account a where a.status = com.ltz.auth.domain.enums.AccountStatus.SUSPENDED "
            + "and a.suspendedUntil is not null and a.suspendedUntil <= :now order by a.suspendedUntil")
    List<Account> findSuspensionsDueForUpdate(Instant now, Pageable page);

    /**
     * Durumu {@code [from, to]} aralığında değişmiş, ama bu hedef için o duruma ait olay satırı (değişimden sonra
     * yazılmış) bulunmayan hesaplar: olay hiç yazılmamış (hedef o sırada kapalıydı) ya da kaybolmuş.
     * PENDING_VERIFICATION yayınlanmaz. SKIP LOCKED: eşzamanlı örnekler aynı hesabı iki kez yeniden göndermez.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select a from Account a
            where a.statusChangedAt between :from and :to
              and a.status <> com.ltz.auth.domain.enums.AccountStatus.PENDING_VERIFICATION
              and not exists (
                select 1 from AuthEvent e
                where e.accountId = a.id and e.target = :target
                  and e.type = com.ltz.auth.domain.enums.AuthEventType.ACCOUNT_STATUS_CHANGED
                  and e.value = cast(a.status as string) and e.occurredAt >= a.statusChangedAt)
            order by a.statusChangedAt
            """)
    List<Account> findStatusEventGapsForUpdate(String target, Instant from, Instant to, Pageable page);
}
