package com.ltz.auth.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ltz.auth.domain.entity.ExternalIdentity;
import com.ltz.auth.domain.enums.ExternalProvider;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {

    Optional<ExternalIdentity> findByProviderAndProviderSubject(ExternalProvider provider, String providerSubject);
}
