package com.ltz.auth.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.ltz.auth.domain.entity.Account;

import jakarta.persistence.LockModeType;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(UUID id);
}
