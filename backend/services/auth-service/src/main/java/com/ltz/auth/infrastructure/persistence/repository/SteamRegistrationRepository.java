package com.ltz.auth.infrastructure.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.ltz.auth.domain.entity.SteamRegistration;

import jakarta.persistence.LockModeType;

public interface SteamRegistrationRepository extends JpaRepository<SteamRegistration, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SteamRegistration r where r.tokenHash = :tokenHash")
    Optional<SteamRegistration> findByTokenHashForUpdate(String tokenHash);
}
