package com.ltz.performance.infrastructure.persistence.repository;

import com.ltz.performance.domain.entity.SyncConsent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncConsentRepository extends JpaRepository<SyncConsent, UUID> { }
