package com.ltz.auth.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ltz.auth.domain.entity.SecurityEvent;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID> {
}
