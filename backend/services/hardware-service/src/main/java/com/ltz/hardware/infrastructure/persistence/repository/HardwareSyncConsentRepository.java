package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.domain.entity.HardwareSyncConsent;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HardwareSyncConsentRepository extends JpaRepository<HardwareSyncConsent, UUID> {
    Optional<HardwareSyncConsent> findByProfileId(UUID profileId);
}
