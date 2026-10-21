package com.ltz.auth.infrastructure.persistence.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.ltz.auth.domain.entity.OAuthFlow;
import com.ltz.auth.domain.enums.ExternalProvider;

public interface OAuthFlowRepository extends JpaRepository<OAuthFlow, String> {

    /** Atomik tüketim: eşzamanlı iki callback'ten yalnız biri 1 döndürür. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update OAuthFlow f set f.consumedAt = :now
            where f.stateHash = :stateHash and f.provider = :provider
              and f.consumedAt is null and f.expiresAt > :now
            """)
    int consume(String stateHash, ExternalProvider provider, Instant now);
}
