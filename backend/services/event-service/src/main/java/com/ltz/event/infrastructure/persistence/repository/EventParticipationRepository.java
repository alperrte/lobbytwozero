package com.ltz.event.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.event.domain.entity.EventParticipation;
import com.ltz.event.domain.enums.ParticipationStatus;

/** Katılım deposu (EVT-08/09). Sayaç ve toplu işlemler kasıtlı olarak native, tek ifadelik, atomik UPDATE'lerdir. */
public interface EventParticipationRepository extends JpaRepository<EventParticipation, UUID> {

	Optional<EventParticipation> findByEventIdAndUserIdAndStatus(UUID eventId, UUID userId, ParticipationStatus status);

	/** Sayfadaki etkinlikler için kullanıcının geçerli (ACTIVE/ATTENDED) katılımları; tek sorgu (N+1 yok). */
	List<EventParticipation> findByUserIdAndEventIdInAndStatusIn(UUID userId, Collection<UUID> eventIds,
			Collection<ParticipationStatus> statuses);

	/**
	 * Kontenjanı tek atomik UPDATE ile ayırır: etkinlik açıksa ve kontenjan varsa sayaç artar. 0 dönerse etkinlik kapalı
	 * ya da doludur. Satır kilidi UPDATE sırasında alınır, eşzamanlı istekler sırayla çalışır; CHECK kısıtı ikinci güvencedir.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			update events set participant_count = participant_count + 1, version = version + 1, updated_at = :now
			where id = :eventId and status in ('PLANNED', 'ACTIVE') and ends_at > :now
			  and (capacity is null or participant_count < capacity)
			""", nativeQuery = true)
	int reserveSeat(@Param("eventId") UUID eventId, @Param("now") Instant now);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			update events set participant_count = participant_count - 1, version = version + 1, updated_at = :now
			where id = :eventId and participant_count > 0
			""", nativeQuery = true)
	int releaseSeat(@Param("eventId") UUID eventId, @Param("now") Instant now);

	/** Hesap silme: düzenleyicisi bu hesap olan açık etkinliklerin geçerli katılımları etkinlik tarafından iptal edilir. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			update event_participations
			set status = 'CANCELLED_BY_EVENT', cancelled_at = :now, updated_at = :now, version = version + 1
			where status = 'ACTIVE'
			  and event_id in (select id from events where organizer_id = :accountId and status in ('PLANNED', 'ACTIVE'))
			""", nativeQuery = true)
	int cancelParticipationsOfOrganizerEvents(@Param("accountId") UUID accountId, @Param("now") Instant now);

	/** Hesap silme: düzenleyicisi bu hesap olan açık etkinlikleri iptal eder ve sayaçlarını sıfırlar. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			update events set status = 'CANCELLED', participant_count = 0, version = version + 1, updated_at = :now
			where organizer_id = :accountId and status in ('PLANNED', 'ACTIVE')
			""", nativeQuery = true)
	int cancelOpenEventsOfOrganizer(@Param("accountId") UUID accountId, @Param("now") Instant now);

	/** Hesap silme: tüm etkinliklerde düzenleyici bağlantısı kaldırılır (KD-3). */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "update events set organizer_id = null, version = version + 1, updated_at = :now where organizer_id = :accountId",
			nativeQuery = true)
	int detachOrganizer(@Param("accountId") UUID accountId, @Param("now") Instant now);

	/** Hesap silme: kullanıcının geçerli katılımlarının tuttuğu kontenjan geri verilir (silmeden ÖNCE çağrılır). */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			update events set participant_count = participant_count - 1, version = version + 1, updated_at = :now
			where participant_count > 0
			  and id in (select event_id from event_participations where user_id = :accountId and status in ('ACTIVE', 'ATTENDED'))
			""", nativeQuery = true)
	int releaseSeatsOfUser(@Param("accountId") UUID accountId, @Param("now") Instant now);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "delete from event_participations where user_id = :accountId", nativeQuery = true)
	int deleteByUser(@Param("accountId") UUID accountId);
}
