package com.ltz.mail.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ltz.mail.domain.entity.MailTemplate;

public interface MailTemplateRepository extends JpaRepository<MailTemplate, UUID> {

    /** Aynı kod ve dil için etkin olan en yüksek sürüm. */
    Optional<MailTemplate> findFirstByCodeAndLocaleAndActiveTrueOrderByTemplateVersionDesc(String code, String locale);
}
