package com.ltz.event.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ltz.event.domain.entity.Event;
import com.ltz.event.domain.enums.EventStatus;

public interface EventRepository extends JpaRepository<Event, UUID> {

	/**
	 * Bitiş zamanı {@code now}'dan sonra olan ve verilen durumlardaki etkinlikleri başlangıç zamanına (sonra kimliğe)
	 * göre artan sıralı, sayfalı döndürür (EVT-04). Sıralama sorgu adında sabittir; {@code pageable} sıralama taşımaz.
	 */
	Page<Event> findByEndsAtAfterAndStatusInOrderByStartsAtAscIdAsc(Instant now, Collection<EventStatus> statuses,
			Pageable pageable);
}
