package com.ltz.content.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.content.api.dto.request.ContentFeedRequest;
import com.ltz.content.api.dto.response.CategoryResponse;
import com.ltz.content.api.dto.response.ContentCardResponse;
import com.ltz.content.api.dto.response.ContentPageResponse;
import com.ltz.content.application.mapper.ContentMapper;
import com.ltz.content.application.port.ContentFeedPort;
import com.ltz.content.application.port.ContentFeedPort.Card;
import com.ltz.content.application.port.ContentFeedPort.Criteria;
import com.ltz.content.application.port.ContentFeedPort.Position;
import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.exception.InvalidContentQueryException;
import com.ltz.content.infrastructure.persistence.repository.ContentCategoryRepository;

/**
 * Herkese açık okuma tarafı (CONT-01/02/03/11/12/13): yalnız yayımlanmış ve silinmemiş içerik listelenir; taslak, kaldırılmış
 * ve silinmiş içerik hiçbir koşulda akışta görünmez. Arama içerik metninde "içerir" biçimindedir (büyük/küçük harf duyarsız).
 */
@Service
public class ContentFeedService {

	static final int DEFAULT_LIMIT = 20;
	static final int MIN_SEARCH_LENGTH = 2;

	private final ContentFeedPort feed;
	private final ContentCategoryRepository categoryRepository;
	private final ContentMapper contentMapper;

	public ContentFeedService(ContentFeedPort feed, ContentCategoryRepository categoryRepository,
			ContentMapper contentMapper) {
		this.feed = feed;
		this.categoryRepository = categoryRepository;
		this.contentMapper = contentMapper;
	}

	@Transactional(readOnly = true)
	public ContentPageResponse list(ContentFeedRequest request) {
		int limit = request.limit() == null ? DEFAULT_LIMIT : request.limit();
		if (request.from() != null && request.to() != null && !request.from().isBefore(request.to())) {
			throw new InvalidContentQueryException("The 'from' date must be before the 'to' date.");
		}
		Position after = request.cursor() == null ? null : PublishedCursor.decode(request.cursor());
		Criteria criteria = new Criteria(searchPattern(request.q()), request.category(), request.gameId(), request.type(),
				request.featured(), request.from(), request.to(), request.locale() == null ? Content.DEFAULT_LOCALE
						: request.locale(), after, limit + 1);

		// limit + 1 satır okunur: fazla satır varsa sonraki sayfa vardır (ayrı COUNT sorgusu yok).
		List<Card> rows = feed.find(criteria);
		boolean hasMore = rows.size() > limit;
		List<Card> cards = hasMore ? rows.subList(0, limit) : rows;

		List<UUID> ids = cards.stream().map(Card::id).toList();
		Map<UUID, List<String>> categories = feed.categoryCodes(ids);
		Map<UUID, UUID> covers = feed.coverImageIds(ids);
		List<ContentCardResponse> items = cards.stream()
				.map(card -> contentMapper.toCard(card, categories.getOrDefault(card.id(), List.of()), covers.get(card.id())))
				.toList();
		String nextCursor = null;
		if (hasMore) {
			Card last = cards.getLast();
			nextCursor = PublishedCursor.encode(last.publishedAt(), last.id());
		}
		return new ContentPageResponse(items, nextCursor);
	}

	@Transactional(readOnly = true)
	public List<CategoryResponse> listCategories() {
		return categoryRepository.findAllByOrderBySortOrderAsc().stream().map(contentMapper::toResponse).toList();
	}

	/**
	 * Boşluklar kırpılır; boş metin "arama yok" sayılır, tek karakterlik arama reddedilir (gürültü ve taramayı sınırlar). LIKE
	 * özel karakterleri ({@code %}, {@code _}) ve kaçış karakteri ({@code !}) kaçırılır; metin kullanıcı girdisidir ve SQL'e
	 * yalnız bağlı parametre olarak girer. Büyük/küçük harf katlaması burada <b>yapılmaz</b>: sorgu hem sütunu hem deseni
	 * veritabanının {@code lower()} fonksiyonuyla katlar. Java'nın {@code toLowerCase} sonucu (ör. {@code İ} → {@code i} +
	 * birleştirici nokta) veritabanınınkinden ({@code İ} → {@code i}) farklı olduğundan, iki ayrı katlama Türkçe {@code İ}
	 * içeren aramaları kaçırırdı.
	 */
	public static String searchPattern(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String text = raw.strip();
		if (text.length() < MIN_SEARCH_LENGTH) {
			throw new InvalidContentQueryException("The search text must be at least " + MIN_SEARCH_LENGTH + " characters long.");
		}
		String escaped = text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
		return "%" + escaped + "%";
	}
}
