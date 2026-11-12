package com.ltz.event.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ltz.event.domain.entity.Event;
import com.ltz.event.domain.enums.EventStatus;

/**
 * Etkinlik deposu. Liste, arama ve filtre sorgusu {@link EventSearchRepository#search} ile tek yerde tanımlıdır
 * (EVT-04, EVT-05, EVT-06).
 */
public interface EventRepository extends JpaRepository<Event, UUID>, EventSearchRepository {

	/** Listelenen kapsamda oyuna bağlı en az bir etkinlik var mı (yoksa oyun adı araması sonucu değiştiremez). */
	boolean existsByEndsAtAfterAndStatusInAndGameIdIsNotNull(Instant now, Collection<EventStatus> statuses);
}
