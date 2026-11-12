package com.ltz.event.application.port;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import com.ltz.event.domain.enums.EventPlatform;
import com.ltz.event.domain.enums.EventStatus;
import com.ltz.event.domain.enums.EventType;

/**
 * Etkinlik listesi/arama/filtre sorgusunun tek tanımı (EVT-04, EVT-05, EVT-06). Tüm koşullar <b>VE</b> ile birleşir:
 * <ul>
 *   <li>kapsam: {@code endsAt > now} ve durum {@code statuses} içinde (zorunlu);</li>
 *   <li>metin araması ({@code titlePattern}, isteğe bağlı): başlık deseni <b>veya</b> {@code textGameIds} içindeki
 *       oyuna bağlı etkinlikler; her etkinlik bir kez döner;</li>
 *   <li>{@code types}: boş değilse tür bu kümede;</li>
 *   <li>{@code platforms}: boş değilse etkinliğin <b>kendi</b> platformlarından en az biri bu kümede; platformu
 *       belirtilmemiş etkinlikler eşleşmez;</li>
 *   <li>{@code gameId}: verilirse etkinlik tam bu oyuna bağlı;</li>
 *   <li>{@code from}/{@code to}: etkinlik süresi [from, to) aralığıyla kesişir
 *       ({@code startsAt < to} ve {@code endsAt > from}).</li>
 * </ul>
 * Sıralama sabittir: başlangıç zamanı, sonra kimlik (artan).
 *
 * @param titlePattern {@code '!'} kaçışlı LIKE deseni (bkz. EventSearch) veya {@code null}
 * @param textGameIds metin aramasıyla bulunan oyun kimlikleri (boş olabilir)
 */
public record EventSearchCriteria(
		Instant now,
		Collection<EventStatus> statuses,
		String titlePattern,
		Set<UUID> textGameIds,
		Set<EventType> types,
		Set<EventPlatform> platforms,
		UUID gameId,
		Instant from,
		Instant to) {

	public EventSearchCriteria {
		textGameIds = textGameIds == null ? Set.of() : Set.copyOf(textGameIds);
		types = types == null ? Set.of() : Set.copyOf(types);
		platforms = platforms == null ? Set.of() : Set.copyOf(platforms);
	}
}
