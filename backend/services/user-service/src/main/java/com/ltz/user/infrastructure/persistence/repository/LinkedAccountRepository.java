package com.ltz.user.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.user.domain.entity.LinkedAccount;
import com.ltz.user.domain.enums.LinkedProvider;

public interface LinkedAccountRepository extends JpaRepository<LinkedAccount, UUID> {

    Optional<LinkedAccount> findByUserIdAndProvider(UUID userId, LinkedProvider provider);

    /** Aynı dış kimlik başka bir hesapta etkin mi (Auth'un tekillik kuralı bozulduysa olay reddedilir). */
    boolean existsByProviderAndExternalIdAndUserIdNot(LinkedProvider provider, String externalId, UUID userId);

    /**
     * Auth'tan gelen "bağlandı" olayı (son-durum upsert). Aynı kimlik yeniden bildirilirse izin ve onay korunur; farklı
     * bir kimlikse izin sıfırlanır. {@code changed_at} daha yeni değilse (sırası bozuk eski olay) hiçbir şey değişmez.
     */
    @Modifying
    @Query(value = """
            INSERT INTO linked_accounts (id, user_id, provider, external_id, active, show_on_profile, consent_at,
                                         linked_at, changed_at, version)
            VALUES (:id, :userId, :provider, :externalId, true, false, NULL, :at, :at, 0)
            ON CONFLICT (user_id, provider) DO UPDATE
                SET show_on_profile = (linked_accounts.active AND linked_accounts.external_id = EXCLUDED.external_id
                                       AND linked_accounts.show_on_profile),
                    consent_at = CASE WHEN linked_accounts.active
                                       AND linked_accounts.external_id = EXCLUDED.external_id
                                      THEN linked_accounts.consent_at END,
                    linked_at = CASE WHEN linked_accounts.active
                                      AND linked_accounts.external_id = EXCLUDED.external_id
                                     THEN linked_accounts.linked_at ELSE EXCLUDED.linked_at END,
                    external_id = EXCLUDED.external_id,
                    active = true,
                    changed_at = EXCLUDED.changed_at,
                    version = linked_accounts.version + 1
                WHERE linked_accounts.changed_at < EXCLUDED.changed_at""", nativeQuery = true)
    int upsertLinked(@Param("id") UUID id, @Param("userId") UUID userId, @Param("provider") String provider,
            @Param("externalId") String externalId, @Param("at") Instant at);

    /** Auth'tan gelen "kaldırıldı" olayı: kimlik ve izin silinir, satır sırayı korumak için kalır. */
    @Modifying
    @Query(value = """
            INSERT INTO linked_accounts (id, user_id, provider, external_id, active, show_on_profile, consent_at,
                                         linked_at, changed_at, version)
            VALUES (:id, :userId, :provider, NULL, false, false, NULL, NULL, :at, 0)
            ON CONFLICT (user_id, provider) DO UPDATE
                SET external_id = NULL, active = false, show_on_profile = false, consent_at = NULL, linked_at = NULL,
                    changed_at = EXCLUDED.changed_at, version = linked_accounts.version + 1
                WHERE linked_accounts.changed_at < EXCLUDED.changed_at""", nativeQuery = true)
    int upsertUnlinked(@Param("id") UUID id, @Param("userId") UUID userId, @Param("provider") String provider,
            @Param("at") Instant at);
}
