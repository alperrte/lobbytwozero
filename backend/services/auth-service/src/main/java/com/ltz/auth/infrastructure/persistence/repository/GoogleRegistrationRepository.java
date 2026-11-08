package com.ltz.auth.infrastructure.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.ltz.auth.domain.entity.GoogleRegistration;

import jakarta.persistence.LockModeType;

public interface GoogleRegistrationRepository extends JpaRepository<GoogleRegistration, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from GoogleRegistration r where r.tokenHash = :tokenHash")
    Optional<GoogleRegistration> findByTokenHashForUpdate(String tokenHash);
}
