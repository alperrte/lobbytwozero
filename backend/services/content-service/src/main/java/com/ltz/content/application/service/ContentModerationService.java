package com.ltz.content.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.content.api.dto.response.ModerationActionResponse;
import com.ltz.content.application.port.CurrentUserPort;
import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.entity.ContentModerationAction;
import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;
import com.ltz.content.domain.enums.ModeratorRole;
import com.ltz.content.domain.enums.ModerationActionType;
import com.ltz.content.domain.exception.ContentNotFoundException;
import com.ltz.content.domain.exception.InsufficientModerationAuthorityException;
import com.ltz.content.domain.exception.InvalidContentRequestException;
import com.ltz.content.domain.exception.ModeratorRequiredException;
import com.ltz.content.infrastructure.persistence.repository.ContentModerationActionRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentRepository;

/**
 * Moderasyon kaynaklı durum değişikliği (CONT-10). Content-service bu değişikliği <b>yalnız admin-service'in</b>
 * {@code ModerationActionTaken} olayıyla uygular (docs/architecture.md: "admin karar → ModerationActionTaken → kaynak servis
 * uygular"); moderatör için doğrudan yazma ucu yoktur. Olayı yalnız Auth servis token'ı taşıyan admin-service gönderebilir
 * (token denetimi {@code SecurityConfig}/{@code InternalEventController}'dadır).
 *
 * <p>Karar yetkisi iki katmanda denetlenir: admin-service kararı veren kullanıcının rolünü ({@code MODERATOR} | {@code ADMIN})
 * doğrulayıp olay yüküne yazar; content-service bu bilgiye servis token'ı geçerliyse güvenir ve içerik türünü kendi
 * veritabanından okuyarak yetkiyi ayrıca denetler: resmî duyuru ({@code ANNOUNCEMENT}) için hem {@code REMOVE} hem
 * {@code RESTORE} yalnız {@code ADMIN} kararıyla uygulanır, normal içerikte {@code MODERATOR} ve {@code ADMIN} yeterlidir.
 * Yetersiz karar reddedilir ({@link InsufficientModerationAuthorityException}); çağıran olay işleme transaction'ı geri
 * alınır, böylece içerik, geçmiş ve inbox kaydı değişmez.
 *
 * <p>Uygulanan her karar, gerekçe, karar veren, rolü ve zamanla değişmez bir kayda yazılır ve içeriğin geçerli durumuna
 * (kaldırma bilgisi) aynı transaction'da yansır. Kaldırma, öne çıkarmayı da sona erdirir. Yalnız yayımlanmış içerik
 * kaldırılır; olay yeniden teslim edilebildiği için geçersiz durum geçişi hata değil etkisizdir.
 */
@Service
public class ContentModerationService {

	private final ContentRepository contentRepository;
	private final ContentModerationActionRepository actionRepository;
	private final CurrentUserPort currentUser;
	private final Clock clock;

	public ContentModerationService(ContentRepository contentRepository, ContentModerationActionRepository actionRepository,
			CurrentUserPort currentUser, Clock clock) {
		this.contentRepository = contentRepository;
		this.actionRepository = actionRepository;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	/**
	 * Admin-service kararı. Dönüş: değişiklik uygulandıysa {@code true}; içerik yoksa ya da durum karara zaten uygunsa
	 * etkisiz ({@code false}). Karar veren yetkisi yetersizse {@link InsufficientModerationAuthorityException} fırlatır
	 * (içerik varsa; durum uygun olsa da olmasa da).
	 */
	@Transactional
	public boolean applyDecision(UUID contentId, ModerationActionType action, UUID moderatorId, ModeratorRole moderatorRole,
			String reason, UUID reportId, UUID eventId) {
		Optional<Content> found = contentRepository.findActiveByIdForUpdate(contentId);
		if (found.isEmpty()) {
			return false;
		}
		Content content = found.get();
		requireAuthority(content, moderatorRole);
		if (!isApplicable(content, action)) {
			return false;
		}
		Instant now = Instant.now(clock);
		if (action == ModerationActionType.REMOVE) {
			content.remove(moderatorId, cleanReason(reason), now);
		} else {
			content.restore(now);
		}
		contentRepository.save(content);
		actionRepository.save(ContentModerationAction.of(contentId, action, moderatorId, moderatorRole, cleanReason(reason), now)
				.viaEvent(reportId, eventId));
		return true;
	}

	/**
	 * İçeriğin moderasyon geçmişi (en yeni önce). Salt okunur; yalnız {@code MODERATOR} ya da {@code ADMIN} rolü (JWT
	 * {@code roles}). Karar veren yetkilinin kimliği yazara gösterilmez.
	 */
	@Transactional(readOnly = true)
	public List<ModerationActionResponse> history(UUID contentId) {
		currentUser.requireUserId();
		if (!currentUser.hasAdminRole() && !currentUser.hasModeratorRole()) {
			throw new ModeratorRequiredException();
		}
		contentRepository.findByIdAndDeletedAtIsNull(contentId).orElseThrow(ContentNotFoundException::new);
		return actionRepository.findByContentIdOrderByCreatedAtDescIdDesc(contentId).stream()
				.map(ContentModerationService::toResponse).toList();
	}

	/** Resmî duyuru yalnız {@code ADMIN} kararıyla değişir; normal içerikte {@code MODERATOR} ve {@code ADMIN} yeterlidir. */
	private static void requireAuthority(Content content, ModeratorRole role) {
		if (role == null || (content.getType() == ContentType.ANNOUNCEMENT && role != ModeratorRole.ADMIN)) {
			throw new InsufficientModerationAuthorityException();
		}
	}

	private static boolean isApplicable(Content content, ModerationActionType action) {
		return action == ModerationActionType.REMOVE ? content.isPublished() : content.isRemoved();
	}

	private static String cleanReason(String raw) {
		String reason = raw == null ? "" : raw.strip();
		if (reason.isEmpty() || reason.length() > ContentModerationAction.REASON_MAX_LENGTH) {
			throw new InvalidContentRequestException("The reason must be 1-" + ContentModerationAction.REASON_MAX_LENGTH
					+ " characters.");
		}
		return reason;
	}

	private static ModerationActionResponse toResponse(ContentModerationAction entry) {
		ContentStatus resulting = entry.getAction() == ModerationActionType.REMOVE ? ContentStatus.REMOVED
				: ContentStatus.PUBLISHED;
		return new ModerationActionResponse(entry.getId(), entry.getContentId(), entry.getAction(), resulting,
				entry.getActorId(), entry.getActorRole(), entry.getSource(), entry.getReason(), entry.getReportId(),
				entry.getCreatedAt());
	}
}
