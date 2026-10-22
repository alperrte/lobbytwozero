package com.ltz.auth.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ltz.auth.domain.entity.AccountStatusHistory;

public interface AccountStatusHistoryRepository extends JpaRepository<AccountStatusHistory, UUID> {
}
