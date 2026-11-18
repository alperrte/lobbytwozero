package com.ltz.event.api.dto.request;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.ltz.event.domain.enums.EventPlatform;
import com.ltz.event.domain.enums.EventType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Etkinlik listesi parametreleri (EVT-04 sayfalama, EVT-05 arama, EVT-06 filtreleme). Sayfa 0'dan başlar. Sınırlar
 * sunucuda zorunludur (docs/SECURITY.md §9): boyut en çok 50, sayfa en çok 1000, arama metni en çok 100 karakter ve
 * kontrol karakteri (NUL dahil) içeremez, en çok 5 tür. Verilmeyen değerler varsayılanı alır (sayfa 0, boyut 20, arama
 * ve filtre yok). Boş veya yalnız boşluktan oluşan arama metni "arama yok" sayılır.
 *
 * <p>Filtreler birbirine ve aramaya <b>VE</b> ile bağlanır:
 * <ul>
 *   <li>{@code type}: tekrarlanabilir ({@code type=TOURNAMENT&type=POLL} veya virgüllü); kümeden biri (VEYA).</li>
 *   <li>{@code platform}: etkinliğin <b>kendi</b> platformu (bağlı oyundan türetilmez); tekrarlanabilir/virgüllü,
 *       kümeden biri (VEYA), en çok 6; platformu belirtilmemiş etkinlikler eşleşmez.</li>
 *   <li>{@code gameId}: etkinliğin bağlı olduğu oyun.</li>
 *   <li>{@code from}/{@code to}: UTC ISO-8601 anları; etkinlik süresi [from, to) aralığıyla kesişir
 *       ({@code startsAt < to} ve {@code endsAt > from}); ikisi de isteğe bağlıdır, {@code to > from} olmalıdır.</li>
 * </ul>
 */
public record EventListRequest(
		@Min(0)
		@Max(EventListRequest.MAX_PAGE)
		Integer page,
		@Min(1)
		@Max(EventListRequest.MAX_SIZE)
		Integer size,
		@Size(max = EventListRequest.MAX_QUERY_LENGTH)
		@Pattern(regexp = "[\\P{Cc}]*", message = "must not contain control characters")
		String q,
		@Size(max = EventListRequest.MAX_TYPES)
		List<EventType> type,
		@Size(max = EventListRequest.MAX_PLATFORMS)
		List<EventPlatform> platform,
		UUID gameId,
		Instant from,
		Instant to) {

	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 50;
	public static final int MAX_PAGE = 1000;
	public static final int MAX_QUERY_LENGTH = 100;
	public static final int MAX_TYPES = 5;
	public static final int MAX_PLATFORMS = 6;

	/** DB'ye bağlanabilecek en küçük/en büyük an (taşan değerler 500 yerine 400 olur). */
	static final Instant MIN_INSTANT = Instant.parse("1970-01-01T00:00:00Z");
	static final Instant MAX_INSTANT = Instant.parse("9999-12-31T23:59:59Z");

	/** Yalnız sayfalama ve arama içeren istek (filtresiz). */
	public static EventListRequest paged(Integer page, Integer size, String q) {
		return new EventListRequest(page, size, q, null, null, null, null, null);
	}

	public int pageOrDefault() {
		return page == null ? 0 : page;
	}

	public int sizeOrDefault() {
		return size == null ? DEFAULT_SIZE : size;
	}

	/** Kırpılmış arama metni; verilmemişse veya boşsa {@code null} (arama yok). */
	public String searchTextOrNull() {
		if (q == null) {
			return null;
		}
		String stripped = q.strip();
		return stripped.isEmpty() ? null : stripped;
	}

	/** Tür filtresi kümesi; verilmemişse (veya boş değer listesi) boş küme = filtre yok. */
	public Set<EventType> typeSet() {
		return type == null ? Set.of() : type.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
	}

	/** Platform filtresi kümesi; verilmemişse boş küme = filtre yok. */
	public Set<EventPlatform> platformSet() {
		return platform == null ? Set.of() : platform.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
	}

	/** {@code from}/{@code to} desteklenen aralıkta olmalı ve ikisi de verildiyse {@code to > from} olmalıdır. */
	@AssertTrue(message = "from and to must be valid UTC instants between 1970 and 9999 and to must be after from")
	public boolean isDateRangeValid() {
		return inBounds(from) && inBounds(to) && (from == null || to == null || to.isAfter(from));
	}

	private static boolean inBounds(Instant value) {
		return value == null || (!value.isBefore(MIN_INSTANT) && !value.isAfter(MAX_INSTANT));
	}
}
