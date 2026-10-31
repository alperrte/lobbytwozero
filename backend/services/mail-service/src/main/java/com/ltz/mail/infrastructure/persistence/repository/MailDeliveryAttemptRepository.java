package com.ltz.mail.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ltz.mail.domain.entity.MailDeliveryAttempt;

public interface MailDeliveryAttemptRepository extends JpaRepository<MailDeliveryAttempt, UUID> {

    List<MailDeliveryAttempt> findByMessageIdOrderByAttemptNoAsc(UUID messageId);
}
