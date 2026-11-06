package com.ltz.content.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.content.api.dto.request.CreateContentRequest;
import com.ltz.content.api.dto.request.UpdateContentRequest;
import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.application.mapper.ContentMapper;
import com.ltz.content.application.port.CurrentUserPort;
import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.entity.ContentTranslation;
import com.ltz.content.domain.entity.ContentTranslationId;
import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;
import com.ltz.content.domain.exception.ContentNotEditableException;
import com.ltz.content.domain.exception.DraftLimitExceededException;
import com.ltz.content.domain.exception.DuplicateContentException;
import com.ltz.content.domain.exception.InvalidContentRequestException;
import com.ltz.content.domain.exception.OfficialAnnouncementForbiddenException;
import com.ltz.content.domain.exception.TooManyContentsException;
import com.ltz.content.infrastructure.persistence.repository.ContentImageMeta;
import com.ltz.content.infrastructure.persistence.repository.ContentImageRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentTranslationRepository;

/**
 * Taslak oluşturma, düzenleme, silme ve okuma (CONT-05/06/07/08/18). Yazar yalnız doğrulanmış oturumdan alınır; istemci
 * yazarı, türü, dili ya da durumu değiştiremez. Taslak doğrulaması yayın doğrulamasından ayrıdır
 * ({@link ContentPublishRules}).
 */
@Service
public class ContentService {

	/** MVP kuralı: bir kullanıcı saatte en çok bu kadar yeni içerik oluşturabilir (silinenler dahil; silme kotayı sıfırlamaz). */
	static final int MAX_CREATIONS_PER_HOUR = 10;

	/** MVP kuralı: bir kullanıcının aynı anda en çok bu kadar etkin taslağı olabilir (sınırsız depolama büyümesini engeller). */
	static final int MAX_ACTIVE_DRAFTS = 20;

	/** Mükerrer içeriği engelleyen kısmi tekil indeksin adı (V101). */
	private static final String DUPLICATE_INDEX = "uq_contents_author_id_type_content_hash";
	private static final int MAX_CAUSE_DEPTH = 10;

	private final ContentRepository contentRepository;
	private final ContentTranslationRepository translationRepository;
	private final ContentImageRepository imageRepository;
	private final ContentAccess contentAccess;
	private final ContentMapper contentMapper;
	private final ContentFingerprint fingerprint;
	private final CurrentUserPort currentUser;
	private final Clock clock;

	public ContentService(ContentRepository contentRepository, ContentTranslationRepository translationRepository,
			ContentImageRepository imageRepository, ContentAccess contentAccess, ContentMapper contentMapper,
			ContentFingerprint fingerprint, CurrentUserPort currentUser, Clock clock) {
		this.contentRepository = contentRepository;
		this.translationRepository = translationRepository;
		this.imageRepository = imageRepository;
		this.contentAccess = contentAccess;
		this.contentMapper = contentMapper;
		this.fingerprint = fingerprint;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@Transactional
	public ContentResponse createDraft(CreateContentRequest request) {
		UUID authorId = currentUser.requireUserId();
		if (request.type() == ContentType.ANNOUNCEMENT && !currentUser.hasAdminRole()) {
			throw new OfficialAnnouncementForbiddenException();
		}
		Instant now = Instant.now(clock);
		requireWithinQuotas(authorId, now);

		String locale = request.locale() == null ? Content.DEFAULT_LOCALE : request.locale();
		String title = request.title().strip();
		String summary = clean(request.summary());
		String body = clean(request.body());
		String hash = fingerprint.of(locale, title, summary, body);
		if (contentRepository.existsByAuthorIdAndTypeAndContentHashAndDeletedAtIsNull(authorId, request.type(), hash)) {
			throw new DuplicateContentException();
		}

		Content content = saveUnique(Content.draft(authorId, request.type(), locale, hash, now));
		ContentTranslation translation = translationRepository
				.save(ContentTranslation.of(content.getId(), locale, title, summary, body));
		return contentMapper.toResponse(content, translation, List.of());
	}

	@Transactional
	public ContentResponse updateDraft(UUID contentId, UpdateContentRequest request) {
		Content content = contentAccess.loadOwned(contentId, currentUser.requireUserId());
		if (!content.isDraft()) {
			throw new ContentNotEditableException();
		}
		ContentTranslation translation = requireTranslation(content);
		Text next = mergeText(translation, request);
		if (!next.equals(Text.of(translation))) {
			applyChange(content, translation, next);
		}
		return toResponse(content, translation);
	}

	/** Verilen alanları mevcut metinle birleştirir: verilmeyen alan korunur, boş özet/gövde alanı temizler. */
	private static Text mergeText(ContentTranslation current, UpdateContentRequest request) {
		if (request.title() == null && request.summary() == null && request.body() == null) {
			throw new InvalidContentRequestException("At least one of title, summary or body is required.");
		}
		String title = request.title() == null ? current.getTitle() : request.title().strip();
		if (title.isEmpty()) {
			throw new InvalidContentRequestException("The title must not be blank.");
		}
		return new Text(title,
				request.summary() == null ? current.getSummary() : clean(request.summary()),
				request.body() == null ? current.getBody() : clean(request.body()));
	}

	/** Mükerrer içeriği reddeder, yeni metni ve parmak izini kaydeder. */
	private void applyChange(Content content, ContentTranslation translation, Text next) {
		String hash = fingerprint.of(content.getDefaultLocale(), next.title(), next.summary(), next.body());
		if (!hash.equals(content.getContentHash()) && contentRepository
				.existsByAuthorIdAndTypeAndContentHashAndDeletedAtIsNullAndIdNot(content.getAuthorId(), content.getType(),
						hash, content.getId())) {
			throw new DuplicateContentException();
		}
		translation.rewrite(next.title(), next.summary(), next.body());
		translationRepository.save(translation);
		content.revise(hash, Instant.now(clock));
		saveUnique(content);
	}

	/**
	 * Sahibi içeriğini siler. Aynı transaction'da: {@code contents} satırına silme işareti konur (satır kalır, saatlik
	 * oluşturma kotası bu satırları saymaya devam eder), metin ({@code content_translations}) ve görsel baytları kalıcı olarak
	 * silinir; silinen metin için saklama süresi 0 gündür. Silinmiş içerik ve görselleri hiçbir yoldan okunamaz; ikinci silme
	 * 404 döner.
	 */
	@Transactional
	public void deleteContent(UUID contentId) {
		Content content = contentAccess.loadOwnedForUpdate(contentId, currentUser.requireUserId());
		if (!content.isDeletableByOwner()) {
			throw new ContentNotEditableException();
		}
		content.markDeleted(Instant.now(clock));
		contentRepository.save(content);
		imageRepository.deleteByContentId(contentId);
		translationRepository.deleteByContentId(contentId);
	}

	@Transactional(readOnly = true)
	public ContentResponse getContent(UUID contentId) {
		Content content = contentAccess.loadViewable(contentId, currentUser.requireUserId());
		return toResponse(content, requireTranslation(content));
	}

	private ContentResponse toResponse(Content content, ContentTranslation translation) {
		List<ContentImageMeta> images = imageRepository.findByContentIdOrderBySortOrderAsc(content.getId());
		return contentMapper.toResponse(content, translation, images);
	}

	private void requireWithinQuotas(UUID authorId, Instant now) {
		if (contentRepository.countByAuthorIdAndCreatedAtAfter(authorId,
				now.minus(Duration.ofHours(1))) >= MAX_CREATIONS_PER_HOUR) {
			throw new TooManyContentsException();
		}
		if (contentRepository.countByAuthorIdAndStatusAndDeletedAtIsNull(authorId,
				ContentStatus.DRAFT) >= MAX_ACTIVE_DRAFTS) {
			throw new DraftLimitExceededException(MAX_ACTIVE_DRAFTS);
		}
	}

	private ContentTranslation requireTranslation(Content content) {
		return translationRepository.findById(new ContentTranslationId(content.getId(), content.getDefaultLocale()))
				.orElseThrow(() -> new IllegalStateException("Content has no translation: " + content.getId()));
	}

	/** Yarışta (iki eşzamanlı istek) mükerrer içeriği veritabanının tekil indeksi yakalar; 409'a çevrilir. */
	private Content saveUnique(Content content) {
		try {
			return contentRepository.saveAndFlush(content);
		} catch (DataIntegrityViolationException exception) {
			if (violatesDuplicateIndex(exception)) {
				throw new DuplicateContentException(exception);
			}
			throw exception;
		}
	}

	private static boolean violatesDuplicateIndex(Throwable failure) {
		Throwable current = failure;
		for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
			if (current.getMessage() != null && current.getMessage().contains(DUPLICATE_INDEX)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	/** Bir çevirinin düzenlenebilir metni; değer eşitliğiyle "değişiklik var mı" sorusunu yanıtlar. */
	private record Text(String title, String summary, String body) {

		static Text of(ContentTranslation translation) {
			return new Text(translation.getTitle(), translation.getSummary(), translation.getBody());
		}
	}

	/** Boş/yalnız boşluk metin "yok" sayılır (null); aksi halde baştaki ve sondaki boşluklar kırpılır. */
	private static String clean(String text) {
		if (text == null) {
			return null;
		}
		String stripped = text.strip();
		return stripped.isEmpty() ? null : stripped;
	}
}
