package com.ltz.event.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.event.api.dto.request.CreateEventRequest;
import com.ltz.event.api.dto.request.EventListRequest;
import com.ltz.event.api.dto.response.EventPageResponse;
import com.ltz.event.api.dto.response.EventResponse;
import com.ltz.event.application.mapper.EventMapper;
import com.ltz.event.application.port.CurrentUserPort;
import com.ltz.event.domain.entity.Event;
import com.ltz.event.domain.enums.EventStatus;
import com.ltz.event.domain.exception.EventCreationForbiddenException;
import com.ltz.event.domain.exception.EventNotFoundException;
import com.ltz.event.infrastructure.persistence.repository.EventRepository;

/**
 * Etkinlik use-case'leri. EVT-01: yetkili kullanıcı yeni etkinlik oluşturur. Etkinlik sahibi yalnız doğrulanmış
 * kimlikten alınır; rol kontrolü Security zincirinde (URL) yapıldığı gibi burada da yeniden doğrulanır (SECURITY.md §4,
 * "servis sınırında yeniden doğrulama").
 */
@Service
public class EventService {

	/** Listede görünen durumlar: iptal edilmiş ve tamamlanmış etkinlikler yaklaşan/mevcut sayılmaz. */
	static final List<EventStatus> LISTED_STATUSES = List.of(EventStatus.PLANNED, EventStatus.ACTIVE);

	private final EventRepository eventRepository;
	private final EventMapper eventMapper;
	private final CurrentUserPort currentUser;
	private final Clock clock;

	public EventService(EventRepository eventRepository, EventMapper eventMapper, CurrentUserPort currentUser,
			Clock clock) {
		this.eventRepository = eventRepository;
		this.eventMapper = eventMapper;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@Transactional
	public EventResponse createEvent(CreateEventRequest request) {
		UUID organizerId = currentUser.requireUserId();
		if (!currentUser.canCreateEvents()) {
			throw new EventCreationForbiddenException();
		}
		Event event = Event.schedule(organizerId, request.type(), request.title().strip(),
				request.description().strip(), request.startsAt(), request.endsAt(), Instant.now(clock));
		return eventMapper.toResponse(eventRepository.save(event));
	}

	/**
	 * Etkinlik detayı (EVT-03). Kimliği doğrulanmış her kullanıcı herhangi bir etkinliği görebilir; görünürlük/gizlilik
	 * modeli yoktur (karar KD-4: misafir erişimi açık karardır). Bulunamayan kimlik için 404 döner.
	 */
	@Transactional(readOnly = true)
	public EventResponse getEvent(UUID eventId) {
		currentUser.requireUserId();
		return eventRepository.findById(eventId).map(eventMapper::toResponse).orElseThrow(EventNotFoundException::new);
	}

	/**
	 * Yaklaşan ve mevcut etkinlikler (EVT-04). "Yaklaşan": henüz başlamamış; "mevcut": başlamış ve henüz bitmemiş. İkisi
	 * zamana göre türetilir ({@code endsAt > şimdi}); durum geçişleri (EVT-16) uygulanana kadar {@code PLANNED} kalan
	 * ama zamanı geçmiş etkinlikler böylece listede görünmez. İptal edilmiş ve tamamlanmış etkinlikler listelenmez.
	 * Başlangıç zamanına göre artan sıralıdır. Kimliği doğrulanmış her kullanıcı listeyi görür (KD-4).
	 */
	@Transactional(readOnly = true)
	public EventPageResponse listEvents(EventListRequest request) {
		currentUser.requireUserId();
		Page<Event> page = eventRepository.findByEndsAtAfterAndStatusInOrderByStartsAtAscIdAsc(Instant.now(clock),
				LISTED_STATUSES, PageRequest.of(request.pageOrDefault(), request.sizeOrDefault()));
		return eventMapper.toPage(page);
	}
}
