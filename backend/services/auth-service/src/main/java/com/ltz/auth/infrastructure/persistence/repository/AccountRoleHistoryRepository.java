package com.ltz.auth.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ltz.auth.domain.entity.AccountRoleHistory;

public interface AccountRoleHistoryRepository extends JpaRepository<AccountRoleHistory, UUID> {
}
