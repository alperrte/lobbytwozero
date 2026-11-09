package com.ltz.event.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.event.domain.entity.Event;
import com.ltz.event.domain.enums.EventStatus;

public interface EventRepository extends JpaRepository<Event, UUID> {

	/**
	 * Bitiş zamanı {@code now}'dan sonra olan ve verilen durumlardaki etkinlikleri başlangıç zamanına (sonra kimliğe)
	 * göre artan sıralı, sayfalı döndürür (EVT-04). Sıralama sorgu adında sabittir; {@code pageable} sıralama taşımaz.
	 */
	Page<Event> findByEndsAtAfterAndStatusInOrderByStartsAtAscIdAsc(Instant now, Collection<EventStatus> statuses,
			Pageable pageable);

	/**
	 * Liste sorgusuyla aynı kapsam (bitmemiş, verilen durumlar, aynı sıralama) içinde başlığa göre arama (EVT-05).
	 * {@code pattern}, {@code '!'} kaçış karakteriyle hazırlanmış LIKE desenidir (bkz. {@code EventSearch}); eşleşme
	 * büyük/küçük harf duyarsızdır (iki taraf da veritabanında {@code lower()} ile küçültülür, Unicode/Türkçe harfler dahil).
	 * Kullanıcı girdisi yalnız bağlı parametre olarak kullanılır.
	 */
	@Query("""
			select e from Event e
			where e.endsAt > :now and e.status in :statuses and lower(e.title) like lower(:pattern) escape '!'
			order by e.startsAt asc, e.id asc
			""")
	Page<Event> searchByTitle(@Param("now") Instant now, @Param("statuses") Collection<EventStatus> statuses,
			@Param("pattern") String pattern, Pageable pageable);

	/**
	 * {@link #searchByTitle} ile aynı kapsam ve sıralama; başlığa <b>veya</b> verilen oyun kimliklerinden birine uyan
	 * etkinlikleri döndürür (EVT-05, oyun adına göre arama). Tek tablo üzerinde tek koşul olduğundan her etkinlik bir
	 * kez döner (iki yoldan eşleşse bile). {@code gameIds} boş olmamalıdır.
	 */
	@Query("""
			select e from Event e
			where e.endsAt > :now and e.status in :statuses
			and (lower(e.title) like lower(:pattern) escape '!' or e.gameId in :gameIds)
			order by e.startsAt asc, e.id asc
			""")
	Page<Event> searchByTitleOrGame(@Param("now") Instant now, @Param("statuses") Collection<EventStatus> statuses,
			@Param("pattern") String pattern, @Param("gameIds") Collection<UUID> gameIds, Pageable pageable);

	/** Listelenen kapsamda oyuna bağlı en az bir etkinlik var mı (yoksa oyun adı araması sonucu değiştiremez). */
	boolean existsByEndsAtAfterAndStatusInAndGameIdIsNotNull(Instant now, Collection<EventStatus> statuses);
}
