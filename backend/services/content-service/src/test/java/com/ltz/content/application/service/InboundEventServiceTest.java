package com.ltz.content.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.content.api.dto.request.InboundEventRequest;
import com.ltz.content.api.dto.response.InboundEventResult;
import com.ltz.content.api.dto.response.InboundEventResult.Outcome;
import com.ltz.content.application.port.InboxPort;
import com.ltz.content.domain.enums.ModerationActionType;
import com.ltz.content.domain.enums.ModeratorRole;
import com.ltz.content.domain.exception.InsufficientModerationAuthorityException;
import com.ltz.content.domain.exception.InvalidInboundEventException;

/** Admin-service olayı: üretici denetimi, yük (karar yetkisi dahil) doğrulaması, inbox tekilleştirmesi ve karar uygulaması. */
class InboundEventServiceTest {

	private static final UUID EVENT_ID = UUID.fromString("0198a3c2-eeee-7d44-9a55-3f1c2b4d5e66");
	private static final UUID CONTENT_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final UUID MODERATOR = UUID.fromString("0198a3c2-8c2f-7d44-9a55-3f1c2b4d5e66");
	private static final UUID REPORT_ID = UUID.fromString("0198a3c2-dddd-7d44-9a55-3f1c2b4d5e66");

	private final InboxPort inbox = mock(InboxPort.class);
	private final ContentModerationService moderation = mock(ContentModerationService.class);
	private final InboundEventService service = new InboundEventService(inbox, moderation);

	private static Map<String, Object> payload(String targetType, Object targetId, String action, Object moderatorId, String role,
			String reason) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("targetType", targetType);
		payload.put("targetId", targetId);
		payload.put("action", action);
		payload.put("moderatorId", moderatorId);
		payload.put("moderatorRole", role);
		payload.put("reason", reason);
		payload.put("reportId", REPORT_ID.toString());
		return payload;
	}

	private static Map<String, Object> removal() {
		return payload("CONTENT", CONTENT_ID.toString(), "REMOVE", MODERATOR.toString(), "MODERATOR", "Spam");
	}

	private static InboundEventRequest event(String type, String producer, Map<String, Object> payload) {
		return new InboundEventRequest(EVENT_ID, type, 1, Instant.parse("2026-11-07T10:00:00Z"), producer, CONTENT_ID, payload);
	}

	private static InboundEventRequest moderationEvent(Map<String, Object> payload) {
		return event("ModerationActionTaken", "admin-service", payload);
	}

	@Test
	void aRemovalDecisionIsAppliedOnceWithTheVerifiedRoleEventAndReport() {
		when(inbox.markProcessed(EVENT_ID, "ModerationActionTaken", "admin-service")).thenReturn(true);

		InboundEventResult result = service.handle(moderationEvent(removal()));

		assertThat(result.outcome()).isEqualTo(Outcome.PROCESSED);
		assertThat(result.eventId()).isEqualTo(EVENT_ID);
		verify(moderation).applyDecision(CONTENT_ID, ModerationActionType.REMOVE, MODERATOR, ModeratorRole.MODERATOR, "Spam",
				REPORT_ID, EVENT_ID);
	}

	@Test
	void aRestoreDecisionByAnAdminIsApplied() {
		when(inbox.markProcessed(any(), anyString(), anyString())).thenReturn(true);

		service.handle(moderationEvent(payload("CONTENT", CONTENT_ID.toString(), "RESTORE", MODERATOR.toString(), "ADMIN", "İtiraz haklı")));

		verify(moderation).applyDecision(CONTENT_ID, ModerationActionType.RESTORE, MODERATOR, ModeratorRole.ADMIN, "İtiraz haklı",
				REPORT_ID, EVENT_ID);
	}

	@Test
	void aRedeliveredEventIsADuplicateAndChangesNothing() {
		when(inbox.markProcessed(any(), anyString(), anyString())).thenReturn(false);

		InboundEventResult result = service.handle(moderationEvent(removal()));

		assertThat(result.outcome()).isEqualTo(Outcome.DUPLICATE);
		verifyNoInteractions(moderation);
	}

	@Test
	void dismissIsRecordedButLeavesTheContentAloneAndNeedsNoRole() {
		when(inbox.markProcessed(any(), anyString(), anyString())).thenReturn(true);

		InboundEventResult result = service.handle(
				moderationEvent(payload("CONTENT", CONTENT_ID.toString(), "DISMISS", null, null, null)));

		assertThat(result.outcome()).isEqualTo(Outcome.PROCESSED);
		verifyNoInteractions(moderation);
	}

	@Test
	void unknownEventTypesAreIgnoredWithoutTouchingTheInbox() {
		InboundEventResult result = service.handle(event("UserBlocked", "social-service", Map.of()));

		assertThat(result.outcome()).isEqualTo(Outcome.IGNORED);
		verifyNoInteractions(inbox, moderation);
	}

	@Test
	void onlyAdminServiceMayPublishModerationDecisions() {
		for (String producer : new String[] { "social-service", "auth-service", "community-service", "content-service" }) {
			InboundEventRequest forged = event("ModerationActionTaken", producer, removal());

			assertThatThrownBy(() -> service.handle(forged)).isInstanceOf(InvalidInboundEventException.class);
		}
		verifyNoInteractions(inbox, moderation);
	}

	@Test
	void invalidPayloadsAreRejectedAndNeverRecordedAsProcessed() {
		Map<String, Object> badTypes = removal();
		badTypes.put("targetId", "uuid-degil");
		@SuppressWarnings("unchecked")
		Map<String, Object>[] invalid = new Map[] {
				payload("POST", CONTENT_ID.toString(), "REMOVE", MODERATOR.toString(), "MODERATOR", "Spam"),
				payload(null, CONTENT_ID.toString(), "REMOVE", MODERATOR.toString(), "MODERATOR", "Spam"),
				payload("CONTENT", null, "REMOVE", MODERATOR.toString(), "MODERATOR", "Spam"),
				payload("CONTENT", CONTENT_ID.toString(), null, MODERATOR.toString(), "MODERATOR", "Spam"),
				payload("CONTENT", CONTENT_ID.toString(), "HIDE", MODERATOR.toString(), "MODERATOR", "Spam"),
				payload("CONTENT", CONTENT_ID.toString(), "remove", MODERATOR.toString(), "MODERATOR", "Spam"),
				payload("CONTENT", CONTENT_ID.toString(), "REMOVE", null, "MODERATOR", "Spam"),
				payload("CONTENT", CONTENT_ID.toString(), "REMOVE", MODERATOR.toString(), "MODERATOR", null),
				payload("CONTENT", CONTENT_ID.toString(), "REMOVE", MODERATOR.toString(), "MODERATOR", "   "),
				payload("CONTENT", CONTENT_ID.toString(), "RESTORE", MODERATOR.toString(), "ADMIN", "a".repeat(501)),
				badTypes };
		for (Map<String, Object> bad : invalid) {
			InboundEventRequest request = moderationEvent(bad);

			assertThatThrownBy(() -> service.handle(request)).isInstanceOf(InvalidInboundEventException.class);
		}
		verify(inbox, never()).markProcessed(any(), anyString(), anyString());
		verifyNoInteractions(moderation);
	}

	@Test
	void aMissingOrUnknownDecisionRoleIsRejectedBeforeAnythingIsRecorded() {
		for (String role : new String[] { null, "", "PLAYER", "CONTENT_CREATOR", "moderator", "ROLE_ADMIN", "ADMIN,MODERATOR" }) {
			InboundEventRequest request = moderationEvent(
					payload("CONTENT", CONTENT_ID.toString(), "REMOVE", MODERATOR.toString(), role, "Spam"));

			assertThatThrownBy(() -> service.handle(request)).isInstanceOf(InvalidInboundEventException.class);
		}
		verify(inbox, never()).markProcessed(any(), anyString(), anyString());
		verifyNoInteractions(moderation);
	}

	@Test
	void anInsufficientDecisionAuthorityIsRejectedByTheServiceSoTheTransactionRollsBack() {
		when(inbox.markProcessed(any(), anyString(), anyString())).thenReturn(true);
		doThrow(new InsufficientModerationAuthorityException()).when(moderation).applyDecision(any(), any(), any(), any(), any(),
				any(), any());
		InboundEventRequest request = moderationEvent(removal());

		assertThatThrownBy(() -> service.handle(request)).isInstanceOf(InsufficientModerationAuthorityException.class);
	}
}
