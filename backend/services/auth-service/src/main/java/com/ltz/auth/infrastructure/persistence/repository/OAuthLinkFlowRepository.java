package com.ltz.auth.infrastructure.persistence.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.ltz.auth.domain.entity.OAuthLinkFlow;
import com.ltz.auth.domain.enums.ExternalProvider;

public interface OAuthLinkFlowRepository extends JpaRepository<OAuthLinkFlow, String> {

    /** Atomik tüketim: eşzamanlı iki callback'ten yalnız biri 1 döndürür. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update OAuthLinkFlow f set f.consumedAt = :now
            where f.stateHash = :stateHash and f.provider = :provider
              and f.consumedAt is null and f.expiresAt > :now
            """)
    int consume(String stateHash, ExternalProvider provider, Instant now);
}
