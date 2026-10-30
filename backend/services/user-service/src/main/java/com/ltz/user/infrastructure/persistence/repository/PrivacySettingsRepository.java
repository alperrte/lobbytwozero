package com.ltz.user.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ltz.user.domain.entity.PrivacySettings;

public interface PrivacySettingsRepository extends JpaRepository<PrivacySettings, UUID> {

    List<PrivacySettings> findByUserIdIn(Collection<UUID> userIds);
}
