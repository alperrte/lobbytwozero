package com.ltz.auth.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ltz.auth.domain.entity.ExternalIdentity;
import com.ltz.auth.domain.enums.ExternalProvider;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {

    Optional<ExternalIdentity> findByProviderAndProviderSubject(ExternalProvider provider, String providerSubject);

    Optional<ExternalIdentity> findByAccountIdAndProvider(UUID accountId, ExternalProvider provider);

    /** Hesaba bağlı tüm harici kimlikler (hesap türü ve profil için). */
    List<ExternalIdentity> findByAccountId(UUID accountId);

    /** Hesabın, verilen sağlayıcı dışındaki harici kimliği var mı (son giriş yöntemi koruması). */
    boolean existsByAccountIdAndProviderNot(UUID accountId, ExternalProvider provider);
}
