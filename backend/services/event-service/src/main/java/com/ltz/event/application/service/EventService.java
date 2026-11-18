package com.ltz.event.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.event.api.dto.request.CreateEventRequest;
import com.ltz.event.api.dto.request.EventListRequest;
import com.ltz.event.api.dto.response.EventPageResponse;
import com.ltz.event.api.dto.response.EventResponse;
import com.ltz.event.api.dto.response.ParticipationResponse;
import com.ltz.event.application.mapper.EventMapper;
import com.ltz.event.application.port.CurrentUserPort;
import com.ltz.event.application.port.EventSearchCriteria;
import com.ltz.event.application.port.GameCatalogPort;
import com.ltz.event.domain.entity.Event;
import com.ltz.event.domain.entity.EventParticipation;
import com.ltz.event.domain.enums.EventPlatform;
import com.ltz.event.domain.enums.EventStatus;
import com.ltz.event.domain.enums.ParticipationStatus;
import com.ltz.event.domain.exception.EventCreationForbiddenException;
import com.ltz.event.domain.exception.EventNotFoundException;
import com.ltz.event.domain.exception.GameNotAvailableException;
import com.ltz.event.domain.exception.ParticipationConflictException;
import com.ltz.event.domain.exception.ParticipationConflictException.Reason;
import com.ltz.event.domain.exception.ParticipationNotFoundException;
import com.ltz.event.infrastructure.persistence.repository.EventParticipationRepository;
import com.ltz.event.infrastructure.persistence.repository.EventRepository;

/**
 * Etkinlik use-case'leri. EVT-01: yetkili kullanıcı yeni etkinlik oluşturur. Etkinlik sahibi yalnız doğrulanmış
 * kimlikten alınır; rol kontrolü Security zincirinde (URL) yapıldığı gibi burada da yeniden doğrulanır (SECURITY.md §4,
 * "servis sınırında yeniden doğrulama"). EVT-08/09/13: katılım, iptal ve katılımcı sınırı.
 */
@Service
public class EventService {

	/** Listede görünen durumlar: iptal edilmiş ve tamamlanmış etkinlikler yaklaşan/mevcut sayılmaz. */
	static final List<EventStatus> LISTED_STATUSES = List.of(EventStatus.PLANNED, EventStatus.ACTIVE);

	/** Kontenjanı tutan (geçerli) katılım durumları. */
	static final List<ParticipationStatus> CURRENT_PARTICIPATIONS = List.of(ParticipationStatus.ACTIVE,
			ParticipationStatus.ATTENDED);

	private final EventRepository eventRepository;
	private final EventParticipationRepository participationRepository;
	private final EventMapper eventMapper;
	private final CurrentUserPort currentUser;
	private final GameCatalogPort gameCatalog;
	private final Clock clock;

	public EventService(EventRepository eventRepository, EventParticipationRepository participationRepository,
			EventMapper eventMapper, CurrentUserPort currentUser, GameCatalogPort gameCatalog, Clock clock) {
		this.eventRepository = eventRepository;
		this.participationRepository = participationRepository;
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
		Event event = Event.schedule(organizerId, request.type(), request.gameId(), platformsOf(request),
				request.capacity(), request.title().strip(), request.description().strip(), request.startsAt(),
				request.endsAt(), Instant.now(clock));
		if (request.gameId() != null && !gameCatalog.isAvailable(request.gameId())) {
			throw new GameNotAvailableException();
		}
		return eventMapper.toResponse(eventRepository.save(event));
	}

	private static Set<EventPlatform> platformsOf(CreateEventRequest request) {
		return request.platforms() == null ? Set.of()
				: request.platforms().stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
	}

	/**
	 * Etkinlik detayı (EVT-03). Kimliği doğrulanmış her kullanıcı herhangi bir etkinliği görebilir; görünürlük/gizlilik
	 * modeli yoktur (karar KD-4: misafir erişimi açık karardır). Yanıt kapasite, katılımcı sayısı, kalan kontenjan ve
	 * izleyicinin kendi katılım durumunu içerir (kullanıcıya özel). Bulunamayan kimlik için 404 döner.
	 */
	@Transactional(readOnly = true)
	public EventResponse getEvent(UUID eventId) {
		UUID viewerId = currentUser.requireUserId();
		Event event = eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
		ParticipationStatus viewerStatus = viewerStatuses(viewerId, List.of(event)).get(eventId);
		return eventMapper.toResponse(event, viewerStatus, joinable(event, viewerId, viewerStatus, Instant.now(clock)));
	}

	/**
	 * Yaklaşan ve mevcut etkinlikler (EVT-04). "Yaklaşan": henüz başlamamış; "mevcut": başlamış ve henüz bitmemiş. İkisi
	 * zamana göre türetilir ({@code endsAt > şimdi}); durum geçişleri (EVT-16) uygulanana kadar {@code PLANNED} kalan
	 * ama zamanı geçmiş etkinlikler böylece listede görünmez. İptal edilmiş ve tamamlanmış etkinlikler listelenmez.
	 * Başlangıç zamanına göre artan sıralıdır. Kimliği doğrulanmış her kullanıcı listeyi görür (KD-4).
	 * Arama metni verilmişse (EVT-05) aynı kapsam içinde etkinlik başlığına (büyük/küçük harf duyarsız "içerir") <b>veya</b>
	 * adı arama metniyle başlayan bir oyuna bağlı etkinliklere uyanlar döner; her etkinlik bir kez. Açıklama aranmaz.
	 * Filtreler (EVT-06: tür, platform, oyun, tarih aralığı) aramaya ve birbirine VE ile bağlanır. Her öğe kapasite ve
	 * izleyicinin katılım durumunu taşır (sayfa için tek ek sorgu).
	 */
	public EventPageResponse listEvents(EventListRequest request) {
		UUID viewerId = currentUser.requireUserId();
		Instant now = Instant.now(clock);
		String searchText = request.searchTextOrNull();
		EventSearchCriteria criteria = new EventSearchCriteria(
				now,
				LISTED_STATUSES,
				searchText == null ? null : EventSearch.containsPattern(searchText),
				searchText == null ? Set.of() : matchingGameIds(now, searchText),
				request.typeSet(),
				request.platformSet(),
				request.gameId(),
				request.from(),
				request.to());
		Page<Event> page = eventRepository.search(criteria,
				PageRequest.of(request.pageOrDefault(), request.sizeOrDefault()));
		Map<UUID, ParticipationStatus> viewerStatuses = viewerStatuses(viewerId, page.getContent());
		return eventMapper.toPage(page, event -> {
			ParticipationStatus viewerStatus = viewerStatuses.get(event.getId());
			return eventMapper.toSummary(event, viewerStatus, joinable(event, viewerId, viewerStatus, now));
		});
	}

	/**
	 * Etkinliğe katıl (EVT-08). Sahip yalnız token'dan gelir. Kurallar: kendi etkinliğine katılınamaz; etkinlik PLANNED/ACTIVE
	 * ve bitmemiş olmalı; kullanıcının aynı etkinlikte geçerli kaydı olamaz (DB kısmi UNIQUE de mükerrerliği engeller);
	 * kontenjan tek atomik UPDATE ile ayrılır, böylece eşzamanlı isteklerde kapasite aşılmaz (EVT-13). Herhangi bir kural
	 * ihlalinde transaction geri alınır, sayaç ve kayıt değişmez. Katılım cezası (EVT-10) henüz uygulanmaz.
	 */
	@Transactional
	public ParticipationResponse joinEvent(UUID eventId) {
		UUID userId = currentUser.requireUserId();
		Instant now = Instant.now(clock);
		Event event = eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
		if (Objects.equals(event.getOrganizerId(), userId)) {
			throw new ParticipationConflictException(Reason.ORGANIZER_CANNOT_JOIN);
		}
		if (!event.isOpenAt(now)) {
			throw new ParticipationConflictException(Reason.EVENT_NOT_OPEN);
		}
		if (participationRepository.findByEventIdAndUserIdAndStatus(eventId, userId, ParticipationStatus.ACTIVE)
				.isPresent()) {
			throw new ParticipationConflictException(Reason.ALREADY_JOINED);
		}
		EventParticipation participation;
		try {
			participation = participationRepository.saveAndFlush(EventParticipation.join(eventId, userId, now));
		} catch (DataIntegrityViolationException _) {
			throw new ParticipationConflictException(Reason.ALREADY_JOINED);
		}
		if (participationRepository.reserveSeat(eventId, now) == 0) {
			// Okuma ile UPDATE arasında etkinlik doldu ya da kapandı: istisna transaction'ı geri alır, kayıt kalmaz.
			boolean full = eventRepository.findById(eventId).map(Event::isFull).orElse(false);
			throw new ParticipationConflictException(full ? Reason.EVENT_FULL : Reason.EVENT_NOT_OPEN);
		}
		return eventMapper.toParticipation(participation);
	}

	/**
	 * Katılımı iptal et (EVT-09). Yalnız kullanıcının kendi ACTIVE kaydı iptal edilebilir (başkasının kaydı 404 görünür:
	 * IDOR). Kayıt silinmez, CANCELLED_BY_USER olur ve kontenjan aynı transaction'da geri verilir. Katılım cezası
	 * (EVT-10/11) henüz uygulanmaz.
	 */
	@Transactional
	public ParticipationResponse cancelParticipation(UUID eventId) {
		UUID userId = currentUser.requireUserId();
		Instant now = Instant.now(clock);
		EventParticipation participation = participationRepository
				.findByEventIdAndUserIdAndStatus(eventId, userId, ParticipationStatus.ACTIVE)
				.orElseThrow(ParticipationNotFoundException::new);
		participation.cancelByUser(now);
		EventParticipation saved = participationRepository.saveAndFlush(participation);
		participationRepository.releaseSeat(eventId, now);
		return eventMapper.toParticipation(saved);
	}

	private Map<UUID, ParticipationStatus> viewerStatuses(UUID viewerId, List<Event> events) {
		if (events.isEmpty()) {
			return Map.of();
		}
		Map<UUID, ParticipationStatus> statuses = new HashMap<>();
		participationRepository
				.findByUserIdAndEventIdInAndStatusIn(viewerId, events.stream().map(Event::getId).toList(),
						CURRENT_PARTICIPATIONS)
				.forEach(participation -> statuses.put(participation.getEventId(), participation.getStatus()));
		return statuses;
	}

	/** İzleyici bu etkinliğe şu an katılabilir mi: açık, dolu değil, kendi etkinliği değil ve geçerli katılımı yok. */
	private static boolean joinable(Event event, UUID viewerId, ParticipationStatus viewerStatus, Instant now) {
		return viewerStatus == null && event.isOpenAt(now) && !event.isFull()
				&& !Objects.equals(event.getOrganizerId(), viewerId);
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
