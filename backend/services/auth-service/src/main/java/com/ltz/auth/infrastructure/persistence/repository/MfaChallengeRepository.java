package com.ltz.auth.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.ltz.auth.domain.entity.MfaChallenge;

import jakarta.persistence.LockModeType;

public interface MfaChallengeRepository extends JpaRepository<MfaChallenge, UUID> {

    @Query("select c from MfaChallenge c where c.challengeHash = :challengeHash")
    Optional<MfaChallenge> findByChallengeHash(String challengeHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from MfaChallenge c where c.challengeHash = :challengeHash")
    Optional<MfaChallenge> findByChallengeHashForUpdate(String challengeHash);
}
