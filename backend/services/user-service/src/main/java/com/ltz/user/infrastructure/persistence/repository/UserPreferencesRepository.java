package com.ltz.user.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ltz.user.domain.entity.UserPreferences;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, UUID> {
}
