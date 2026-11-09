package com.ltz.user.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ltz.user.domain.entity.UserConsents;

public interface UserConsentsRepository extends JpaRepository<UserConsents, UUID> {

    List<UserConsents> findByUserIdIn(Collection<UUID> userIds);
}
