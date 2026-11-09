package com.ltz.event.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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
import com.ltz.event.application.port.GameCatalogPort;
import com.ltz.event.domain.entity.Event;
import com.ltz.event.domain.enums.EventStatus;
import com.ltz.event.domain.exception.EventCreationForbiddenException;
import com.ltz.event.domain.exception.EventNotFoundException;
import com.ltz.event.domain.exception.GameNotAvailableException;
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
	private final GameCatalogPort gameCatalog;
	private final Clock clock;

	public EventService(EventRepository eventRepository, EventMapper eventMapper, CurrentUserPort currentUser,
			GameCatalogPort gameCatalog, Clock clock) {
		this.eventRepository = eventRepository;
		this.eventMapper = eventMapper;
		this.currentUser = currentUser;
		this.gameCatalog = gameCatalog;
		this.clock = clock;
	}

	/**
	 * Dış servis çağrısı (gameId doğrulaması) bağlantı havuzunu tutmasın diye bilinçli olarak transaction dışındadır;
	 * yalnız {@code save} kendi kısa transaction'ında çalışır. Zaman kuralları (yerel) dış çağrıdan önce doğrulanır.
	 */
	public EventResponse createEvent(CreateEventRequest request) {
		UUID organizerId = currentUser.requireUserId();
		if (!currentUser.canCreateEvents()) {
			throw new EventCreationForbiddenException();
		}
		Event event = Event.schedule(organizerId, request.type(), request.gameId(), request.title().strip(),
				request.description().strip(), request.startsAt(), request.endsAt(), Instant.now(clock));
		if (request.gameId() != null && !gameCatalog.isAvailable(request.gameId())) {
			throw new GameNotAvailableException();
		}
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
	 * Arama metni verilmişse (EVT-05) aynı kapsam içinde etkinlik başlığına (büyük/küçük harf duyarsız "içerir") <b>veya</b>
	 * adı arama metniyle başlayan bir oyuna bağlı etkinliklere uyanlar döner; her etkinlik bir kez. Açıklama aranmaz.
	 */
	public EventPageResponse listEvents(EventListRequest request) {
		currentUser.requireUserId();
		Instant now = Instant.now(clock);
		PageRequest paging = PageRequest.of(request.pageOrDefault(), request.sizeOrDefault());
		String searchText = request.searchTextOrNull();
		Page<Event> page;
		if (searchText == null) {
			page = eventRepository.findByEndsAtAfterAndStatusInOrderByStartsAtAscIdAsc(now, LISTED_STATUSES, paging);
		} else {
			String pattern = EventSearch.containsPattern(searchText);
			Set<UUID> gameIds = matchingGameIds(now, searchText);
			page = gameIds.isEmpty()
					? eventRepository.searchByTitle(now, LISTED_STATUSES, pattern, paging)
					: eventRepository.searchByTitleOrGame(now, LISTED_STATUSES, pattern, gameIds, paging);
		}
		return eventMapper.toPage(page);
	}

	/**
	 * Oyun adı araması (EVT-05, karar KD-5/B): Game Service'te adı {@code searchText} ile başlayan oyunların kimlikleri
	 * <b>eksiksiz</b> alınır. Listelenen kapsamda oyuna bağlı hiç etkinlik yoksa oyun sonucu çıktıyı değiştiremeyeceğinden
	 * Game Service'e gidilmez. Game Service erişilemezse {@code GameCatalogUnavailableException} (503) yayılır; eksik
	 * sonuç döndürülmez. Çağrı DB transaction'ı dışındadır (bağlantı havuzu küçüktür).
	 */
	private Set<UUID> matchingGameIds(Instant now, String searchText) {
		if (!eventRepository.existsByEndsAtAfterAndStatusInAndGameIdIsNotNull(now, LISTED_STATUSES)) {
			return Set.of();
		}
		return gameCatalog.findGameIdsByNamePrefix(searchText);
	}
}
