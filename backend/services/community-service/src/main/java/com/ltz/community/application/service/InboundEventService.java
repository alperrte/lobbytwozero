package com.ltz.community.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.community.api.dto.request.InboundEventRequest;
import com.ltz.community.api.dto.response.InboundEventResult;
import com.ltz.community.api.dto.response.InboundEventResult.Outcome;
import com.ltz.community.application.port.InboxPort;
import com.ltz.community.domain.enums.ModerationAction;
import com.ltz.community.domain.enums.ReportTarget;
import com.ltz.community.domain.exception.InvalidInboundEventException;
import com.ltz.community.infrastructure.persistence.repository.UserBlockRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Diğer servislerin community-service'e ilettiği olayları işler (docs/architecture.md §6.2). Olay, inbox'ta
 * {@code eventId} ile tekilleştirilir ve etkisiyle aynı transaction'da uygulanır; böylece en az bir kez teslim edilen
 * olay iş etkisini en fazla bir kez yaratır. Üretici, olay türüyle eşleşmiyorsa reddedilir (örn. engelleme olayını
 * yalnız social-service, moderasyon kararını yalnız admin-service, hesap durumunu yalnız auth-service yayınlayabilir).
 * {@code AccountStatusChanged} kabul edilir ve tekilleştirilir ama içeriğe etki etmez. Bilinmeyen olay türü başarıyla
 * yok sayılır; üretici sonsuz yeniden denemesin diye.
 */
@Service
public class InboundEventService {

	static final String MODERATION_ACTION_TAKEN = "ModerationActionTaken";
	static final String USER_BLOCKED = "UserBlocked";
	static final String USER_UNBLOCKED = "UserUnblocked";
	static final String ACCOUNT_STATUS_CHANGED = "AccountStatusChanged";
	static final String ADMIN_PRODUCER = "admin-service";
	static final String SOCIAL_PRODUCER = "social-service";
	static final String AUTH_PRODUCER = "auth-service";

	private final InboxPort inbox;
	private final ModerationService moderationService;
	private final UserBlockRepository userBlockRepository;
	private final Clock clock;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public InboundEventService(InboxPort inbox, ModerationService moderationService,
			UserBlockRepository userBlockRepository, Clock clock) {
		this.inbox = inbox;
		this.moderationService = moderationService;
		this.userBlockRepository = userBlockRepository;
		this.clock = clock;
	}

	@Transactional
	public InboundEventResult handle(InboundEventRequest event) {
		String expectedProducer = switch (event.eventType()) {
			case MODERATION_ACTION_TAKEN -> ADMIN_PRODUCER;
			case USER_BLOCKED, USER_UNBLOCKED -> SOCIAL_PRODUCER;
			case ACCOUNT_STATUS_CHANGED -> AUTH_PRODUCER;
			default -> null;
		};
		if (expectedProducer == null) {
			return new InboundEventResult(event.eventId(), Outcome.IGNORED);
		}
		if (!expectedProducer.equals(event.producer())) {
			throw new InvalidInboundEventException("The producer is not allowed to publish this event type.");
		}
		// Yük ayrıştırması inbox kaydından önce yapılır: geçersiz olay "işlendi" diye kaydedilmez.
		Runnable effect = switch (event.eventType()) {
			case MODERATION_ACTION_TAKEN -> moderation(event.payload());
			case USER_BLOCKED -> block(event.payload(), event.occurredAt());
			// Askıya alma/yasak içeriği gizlemez (ürün kararı, Auth zorunlu kılmıyor); olay yalnız kayda geçer.
			case ACCOUNT_STATUS_CHANGED -> accountStatus(event.payload());
			default -> unblock(event.payload());
		};
		if (!inbox.markProcessed(event.eventId(), event.eventType(), event.producer())) {
			return new InboundEventResult(event.eventId(), Outcome.DUPLICATE);
		}
		effect.run();
		return new InboundEventResult(event.eventId(), Outcome.PROCESSED);
	}

	private Runnable moderation(Map<String, Object> payload) {
		ModerationPayload parsed = parse(payload, ModerationPayload.class);
		if (parsed.targetType() == null || parsed.targetId() == null || parsed.action() == null) {
			throw new InvalidInboundEventException("targetType, targetId and action are required.");
		}
		return () -> moderationService.apply(parsed.targetType(), parsed.targetId(), parsed.action(), parsed.reportId());
	}

	private Runnable block(Map<String, Object> payload, Instant occurredAt) {
		BlockPayload parsed = requireBlockPayload(parse(payload, BlockPayload.class));
		// Yansıtma zamanı olayın gerçekleştiği andır (yeniden teslimde de aynı kalır).
		Instant blockedAt = occurredAt == null ? Instant.now(clock) : occurredAt;
		return () -> userBlockRepository.insertIfAbsent(parsed.blockerId(), parsed.blockedId(), blockedAt);
	}

	private Runnable unblock(Map<String, Object> payload) {
		BlockPayload parsed = requireBlockPayload(parse(payload, BlockPayload.class));
		return () -> userBlockRepository.deleteBlock(parsed.blockerId(), parsed.blockedId());
	}

	/** Yük doğrulanır ama içeriğe etki edilmez: yeni durum community'de kayda geçer, gizleme kararı ürün tarafındadır. */
	private Runnable accountStatus(Map<String, Object> payload) {
		AccountStatusPayload parsed = parse(payload, AccountStatusPayload.class);
		if (parsed.accountId() == null || parsed.status() == null || parsed.status().isBlank()) {
			throw new InvalidInboundEventException("accountId and status are required.");
		}
		return () -> {
		};
	}

	private static BlockPayload requireBlockPayload(BlockPayload payload) {
		if (payload.blockerId() == null || payload.blockedId() == null || payload.blockerId().equals(payload.blockedId())) {
			throw new InvalidInboundEventException("blockerId and blockedId are required and must differ.");
		}
		return payload;
	}

	private <T> T parse(Map<String, Object> payload, Class<T> type) {
		try {
			return jsonMapper.convertValue(payload, type);
		} catch (JacksonException | IllegalArgumentException _) {
			throw new InvalidInboundEventException("The event payload is invalid.");
		}
	}

	/** {@code ModerationActionTaken} yükü; {@code reportId} yalnız tek bir raporu hedeflemek içindir. */
	record ModerationPayload(ReportTarget targetType, UUID targetId, ModerationAction action, UUID reportId) {
	}

	/** {@code AccountStatusChanged} yükü (Auth: hesap kimliği ve yeni durum adı, örn. {@code BANNED}). */
	record AccountStatusPayload(UUID accountId, String status) {
	}

	/** {@code UserBlocked} / {@code UserUnblocked} yükü. */
	record BlockPayload(UUID blockerId, UUID blockedId) {
	}
}
