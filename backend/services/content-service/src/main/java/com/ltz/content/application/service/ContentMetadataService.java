package com.ltz.content.application.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.content.api.dto.request.ReplaceCategoriesRequest;
import com.ltz.content.api.dto.request.ReplaceSourcesRequest;
import com.ltz.content.api.dto.request.SourceRequest;
import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.application.port.CurrentUserPort;
import com.ltz.content.application.port.GameCatalogPort;
import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.entity.ContentCategory;
import com.ltz.content.domain.entity.ContentCategoryAssignment;
import com.ltz.content.domain.entity.ContentSource;
import com.ltz.content.domain.exception.ContentNotEditableException;
import com.ltz.content.domain.exception.GameNotAvailableException;
import com.ltz.content.domain.exception.InvalidContentRequestException;
import com.ltz.content.infrastructure.persistence.repository.ContentCategoryAssignmentRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentCategoryRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentSourceRepository;

/**
 * İçerik üst verisi: kategoriler (CONT-03), kaynaklar (CONT-16) ve ilişkili oyun (CONT-01/13). Hepsi yalnız sahibi için ve
 * yalnız taslak ya da yayımlanmış içerikte geçerlidir; kategori ve kaynak listeleri tamamen değiştirilir (boş liste temizler).
 * Oyun bağlantısı game-service'te doğrulanır; bu dış çağrı transaction dışında yapılır (bağlantı havuzu küçüktür), çakışma
 * {@code @Version} ile yakalanır.
 */
@Service
public class ContentMetadataService {

	private final ContentRepository contentRepository;
	private final ContentCategoryRepository categoryRepository;
	private final ContentCategoryAssignmentRepository assignmentRepository;
	private final ContentSourceRepository sourceRepository;
	private final ContentAccess contentAccess;
	private final ContentViewAssembler viewAssembler;
	private final GameCatalogPort gameCatalog;
	private final CurrentUserPort currentUser;
	private final Clock clock;

	public ContentMetadataService(ContentRepository contentRepository, ContentCategoryRepository categoryRepository,
			ContentCategoryAssignmentRepository assignmentRepository, ContentSourceRepository sourceRepository,
			ContentAccess contentAccess, ContentViewAssembler viewAssembler, GameCatalogPort gameCatalog,
			CurrentUserPort currentUser, Clock clock) {
		this.contentRepository = contentRepository;
		this.categoryRepository = categoryRepository;
		this.assignmentRepository = assignmentRepository;
		this.sourceRepository = sourceRepository;
		this.contentAccess = contentAccess;
		this.viewAssembler = viewAssembler;
		this.gameCatalog = gameCatalog;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@Transactional
	public ContentResponse replaceCategories(UUID contentId, ReplaceCategoriesRequest request) {
		Content content = editableOwnedForUpdate(contentId);
		Set<String> codes = new LinkedHashSet<>(request.categories());
		if (codes.size() > ContentCategoryAssignment.MAX_PER_CONTENT) {
			throw new InvalidContentRequestException(
					"At most " + ContentCategoryAssignment.MAX_PER_CONTENT + " categories are allowed.");
		}
		List<ContentCategory> categories = codes.isEmpty() ? List.of() : categoryRepository.findByCodeIn(codes);
		if (categories.size() != codes.size()) {
			throw new InvalidContentRequestException("One or more category codes are unknown.");
		}
		assignmentRepository.deleteByContentId(contentId);
		assignmentRepository.saveAll(categories.stream()
				.map(category -> ContentCategoryAssignment.of(contentId, category.getId())).toList());
		touch(content);
		return viewAssembler.assemble(content, content.getDefaultLocale());
	}

	@Transactional
	public ContentResponse replaceSources(UUID contentId, ReplaceSourcesRequest request) {
		Content content = editableOwnedForUpdate(contentId);
		List<ContentSource> sources = new ArrayList<>();
		for (SourceRequest source : request.sources()) {
			sources.add(ContentSource.of(contentId, source.title().strip(), requireSafeHttpsUrl(source.url()), source.date(),
					sources.size()));
		}
		sourceRepository.deleteByContentId(contentId);
		sourceRepository.saveAll(sources);
		touch(content);
		return viewAssembler.assemble(content, content.getDefaultLocale());
	}

	/** Oyun game-service'te doğrulanır (transaction dışında); ardından bağlantı kısa bir yazmayla kaydedilir. */
	public ContentResponse linkGame(UUID contentId, UUID gameId) {
		Content content = editableOwned(contentId);
		if (!gameId.equals(content.getGameId())) {
			if (!gameCatalog.isAvailable(gameId)) {
				throw new GameNotAvailableException();
			}
			content.linkGame(gameId, Instant.now(clock));
			contentRepository.save(content);
		}
		return viewAssembler.assemble(content, content.getDefaultLocale());
	}

	@Transactional
	public ContentResponse unlinkGame(UUID contentId) {
		Content content = editableOwnedForUpdate(contentId);
		if (content.unlinkGame(Instant.now(clock))) {
			contentRepository.save(content);
		}
		return viewAssembler.assemble(content, content.getDefaultLocale());
	}

	private Content editableOwned(UUID contentId) {
		return requireEditable(contentAccess.loadOwned(contentId, currentUser.requireUserId()));
	}

	private Content editableOwnedForUpdate(UUID contentId) {
		return requireEditable(contentAccess.loadOwnedForUpdate(contentId, currentUser.requireUserId()));
	}

	private static Content requireEditable(Content content) {
		if (!content.isEditableByOwner()) {
			throw new ContentNotEditableException();
		}
		return content;
	}

	private void touch(Content content) {
		content.touch(Instant.now(clock));
		contentRepository.save(content);
	}

	/**
	 * Kaynak bağlantısı yalnız {@code https://} olabilir, sunucu adı içermeli, kullanıcı bilgisi ({@code user:pass@}) ve
	 * boşluk taşımamalıdır. Adres yalnız saklanır ve gösterilir; sunucu ona hiçbir zaman istek atmaz.
	 */
	private static String requireSafeHttpsUrl(String raw) {
		String url = raw.strip();
		try {
			URI uri = new URI(url);
			boolean valid = "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank()
					&& uri.getUserInfo() == null && url.length() <= ContentSource.URL_MAX_LENGTH;
			if (!valid) {
				throw new InvalidContentRequestException("A source link must be a valid https:// address.");
			}
			return url;
		} catch (URISyntaxException exception) {
			throw new InvalidContentRequestException("A source link must be a valid https:// address.", exception);
		}
	}
}
