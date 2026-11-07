package com.ltz.content.application.port;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ltz.content.domain.enums.ContentType;

/**
 * Herkese açık içerik akışı (CONT-01/12/13): yalnız yayımlanmış ve silinmemiş içerik listelenir. Sıralama yayınlanma zamanına
 * göre yeniden eskiye, eşitlikte {@code id} azalan; sayfalama keyset imleciyle yapılır (COUNT yok).
 */
public interface ContentFeedPort {

	/**
	 * Sorgu kriteri. {@code searchPattern} hazır LIKE desenidir (küçük harf, kaçışlı); {@code locale} gösterilecek dildir
	 * (çeviri yoksa içeriğin ilk dili kullanılır). {@code from} dahil, {@code to} hariç yayınlanma zamanıdır.
	 */
	record Criteria(String searchPattern, String categoryCode, UUID gameId, ContentType type, Boolean featured,
			Instant from, Instant to, String locale, Position after, int limit) {
	}

	/** Keyset imleci: bu konumdan daha eski kayıtlar getirilir. */
	record Position(Instant publishedAt, UUID id) {
	}

	/** Liste kartı; gösterilen dildeki başlık ve özet ({@code locale} gerçekten sunulan dildir). */
	record Card(UUID id, UUID authorId, ContentType type, String locale, String title, String summary, UUID gameId,
			boolean featured, Instant publishedAt) {
	}

	/** En çok {@code limit} kart döndürür; çağıran sonraki sayfa var mı diye {@code limit + 1} ister. */
	List<Card> find(Criteria criteria);

	/** Verilen içeriklerin kategori kodları (içerik kimliğine göre, kategori sırasında). */
	Map<UUID, List<String>> categoryCodes(Collection<UUID> contentIds);

	/** Verilen içeriklerin ilk (en küçük sıra numaralı) görselinin kimliği; görseli olmayan içerik haritada yoktur. */
	Map<UUID, UUID> coverImageIds(Collection<UUID> contentIds);
}
