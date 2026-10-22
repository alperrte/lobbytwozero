package com.ltz.auth.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.ltz.auth.domain.entity.AttemptThrottle;

import jakarta.persistence.LockModeType;

public interface AttemptThrottleRepository extends JpaRepository<AttemptThrottle, String> {

    /** Eşzamanlı ilk denemelerde tek satır oluşur; ardından satır kilitlenerek güncellenir. */
    @Modifying
    @Query(value = """
            insert into attempt_throttles (key_hash, failed_attempts, window_started_at, updated_at)
            values (:keyHash, 0, :now, :now)
            on conflict (key_hash) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(String keyHash, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AttemptThrottle t where t.keyHash = :keyHash")
    Optional<AttemptThrottle> findByKeyHashForUpdate(String keyHash);
}
