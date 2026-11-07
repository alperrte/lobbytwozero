package com.ltz.content.application.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.content.api.dto.request.InboundEventRequest;
import com.ltz.content.api.dto.response.InboundEventResult;
import com.ltz.content.api.dto.response.InboundEventResult.Outcome;
import com.ltz.content.application.port.InboxPort;
import com.ltz.content.domain.entity.ContentModerationAction;
import com.ltz.content.domain.enums.ModerationActionType;
import com.ltz.content.domain.enums.ModeratorRole;
import com.ltz.content.domain.exception.InvalidInboundEventException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Diğer servislerin content-service'e ilettiği olayları işler (docs/architecture.md §6.2). Şu an tek olay kabul edilir:
 * admin-service'in {@code ModerationActionTaken} kararı (CONT-10). Olay, inbox'ta {@code eventId} ile tekilleştirilir ve
 * etkisiyle aynı transaction'da uygulanır; böylece en az bir kez teslim edilen olay iş etkisini en fazla bir kez yaratır.
 * Üretici olay türüyle eşleşmiyorsa reddedilir. Bilinmeyen olay türü başarıyla yok sayılır; üretici sonsuz yeniden denemesin
 * diye.
 *
 * <p>Yük sözleşmesi (admin-service ile ortak, bkz. docs/api.md → CONTENT → İç olay girişi): {@code targetType=CONTENT},
 * {@code targetId}, {@code action} ({@code REMOVE} | {@code RESTORE} | {@code DISMISS}); {@code REMOVE}/{@code RESTORE} için
 * ayrıca {@code moderatorId}, {@code moderatorRole} (kararı veren kullanıcının admin-service tarafından doğrulanmış
 * rolü: {@code MODERATOR} | {@code ADMIN}) ve {@code reason} zorunludur, {@code reportId} isteğe bağlıdır. Rol eksik ya da
 * tanınmıyorsa olay 400 ile reddedilir; içerik türüne göre yeterli değilse (resmî duyuru için {@code ADMIN} gerekir) 403 ile
 * reddedilir ve olay işlenmiş sayılmaz. {@code DISMISS} içeriği
 * değiştirmez.
 */
@Service
public class InboundEventService {

	static final String MODERATION_ACTION_TAKEN = "ModerationActionTaken";
	static final String ADMIN_PRODUCER = "admin-service";
	static final String CONTENT_TARGET = "CONTENT";
	static final String DISMISS = "DISMISS";

	private final InboxPort inbox;
	private final ContentModerationService moderationService;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public InboundEventService(InboxPort inbox, ContentModerationService moderationService) {
		this.inbox = inbox;
		this.moderationService = moderationService;
	}

	@Transactional
	public InboundEventResult handle(InboundEventRequest event) {
		if (!MODERATION_ACTION_TAKEN.equals(event.eventType())) {
			return new InboundEventResult(event.eventId(), Outcome.IGNORED);
		}
		if (!ADMIN_PRODUCER.equals(event.producer())) {
			throw new InvalidInboundEventException("The producer is not allowed to publish this event type.");
		}
		// Yük ayrıştırması inbox kaydından önce yapılır: geçersiz olay "işlendi" diye kaydedilmez.
		ModerationPayload payload = parse(event.payload());
		if (!inbox.markProcessed(event.eventId(), event.eventType(), event.producer())) {
			return new InboundEventResult(event.eventId(), Outcome.DUPLICATE);
		}
		if (!DISMISS.equals(payload.action())) {
			moderationService.applyDecision(payload.targetId(), ModerationActionType.valueOf(payload.action()),
					payload.moderatorId(), ModeratorRole.valueOf(payload.moderatorRole()), payload.reason(), payload.reportId(),
					event.eventId());
		}
		return new InboundEventResult(event.eventId(), Outcome.PROCESSED);
	}

	private ModerationPayload parse(Map<String, Object> raw) {
		ModerationPayload payload = convert(raw);
		requireTarget(payload);
		if (!DISMISS.equals(payload.action())) {
			requireDecision(payload);
		}
		return payload;
	}

	private ModerationPayload convert(Map<String, Object> raw) {
		try {
			return jsonMapper.convertValue(raw, ModerationPayload.class);
		} catch (JacksonException | IllegalArgumentException _) {
			throw new InvalidInboundEventException("The event payload is invalid.");
		}
	}

	private static void requireTarget(ModerationPayload payload) {
		if (!CONTENT_TARGET.equals(payload.targetType()) || payload.targetId() == null || payload.action() == null) {
			throw new InvalidInboundEventException("targetType must be CONTENT, and targetId and action are required.");
		}
	}

	/** REMOVE/RESTORE: eylem tanınmalı, karar veren moderatör ve 1-500 karakterlik gerekçe bulunmalı. */
	private static void requireDecision(ModerationPayload payload) {
		if (!isDecisionAction(payload.action())) {
			throw new InvalidInboundEventException("action must be one of REMOVE, RESTORE, DISMISS.");
		}
		if (payload.moderatorId() == null || !isValidReason(payload.reason())) {
			throw new InvalidInboundEventException("moderatorId and a reason of 1-500 characters are required.");
		}
		if (!isKnownRole(payload.moderatorRole())) {
			throw new InvalidInboundEventException("moderatorRole must be MODERATOR or ADMIN.");
		}
	}

	private static boolean isValidReason(String reason) {
		return reason != null && !reason.isBlank() && reason.strip().length() <= ContentModerationAction.REASON_MAX_LENGTH;
	}

	private static boolean isKnownRole(String role) {
		for (ModeratorRole known : ModeratorRole.values()) {
			if (known.name().equals(role)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isDecisionAction(String action) {
		for (ModerationActionType type : ModerationActionType.values()) {
			if (type.name().equals(action)) {
				return true;
			}
		}
		return false;
	}

	/** {@code ModerationActionTaken} yükü (content hedefi). */
	record ModerationPayload(String targetType, UUID targetId, String action, UUID moderatorId, String moderatorRole,
			String reason, UUID reportId) {
	}
}
