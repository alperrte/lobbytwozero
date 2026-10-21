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
}
