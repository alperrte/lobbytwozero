package com.ltz.event.infrastructure.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ltz.event.application.port.EventSearchCriteria;
import com.ltz.event.domain.entity.Event;

/** {@link EventRepository}'ye eklenen özel parça: kriter tabanlı sayfalı etkinlik sorgusu (EVT-04/05/06). */
public interface EventSearchRepository {

	/**
	 * Kriterlere uyan etkinlikleri başlangıç zamanına (sonra kimliğe) göre artan sıralı, sayfalı döndürür; toplam sayı
	 * aynı koşullarla hesaplanır. {@code pageable} sıralama taşımaz (sıra sabittir).
	 */
	Page<Event> search(EventSearchCriteria criteria, Pageable pageable);
}
