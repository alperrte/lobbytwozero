package com.ltz.messaging.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.enums.MemberRole;
import com.ltz.messaging.domain.enums.MessageType;
import com.ltz.messaging.infrastructure.config.MessageProperties;
import com.ltz.messaging.infrastructure.persistence.repository.HiddenMessageRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageReactionRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageRepository;

/** Okuma modeli süslemesi sayfa başına sabit sayıda sorgu yapar (mesaj başına sorgu yok) ve silme hakkını hesaplar. */
class MessageViewsTest {

    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    private static final Duration WINDOW = Duration.ofHours(48);

    private final MessageAttachments attachments = mock(MessageAttachments.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final HiddenMessageRepository hidden = mock(HiddenMessageRepository.class);
    private final MessageReactionRepository reactions = mock(MessageReactionRepository.class);
    private final MessageViews views = new MessageViews(attachments, messages, hidden, reactions,
            new MessageProperties(30, 50, 200, 20, 100, WINDOW), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void decorateSayfaBasinaSabitSorguYapar() {
        UUID viewer = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        ConversationMember member = ConversationMember.join(conversation, viewer, MemberRole.MEMBER, NOW, 1);
        List<MessageView> page = IntStream.range(0, 50)
                .mapToObj(index -> view(conversation, index + 1L, UUID.randomUUID(), UUID.randomUUID(), NOW))
                .toList();
        when(attachments.attachTo(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<MessageView> decorated = views.decorate(viewer, Map.of(conversation, member), page);

        assertThat(decorated).hasSize(50);
        verify(attachments, times(1)).attachTo(anyList());
        verify(messages, times(1)).findAllById(anyCollection());
        verify(hidden, times(1)).findHiddenAmong(any(), anyCollection());
        verify(reactions, times(1)).summarize(anyCollection());
        verify(reactions, times(1)).findMine(any(), anyCollection());
    }

    @Test
    void silmeHakkiGonderenVeYoneticiIcinPencereSonu() {
        UUID sender = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        MessageView fresh = view(conversation, 1, sender, null, NOW.minus(Duration.ofHours(1)));
        MessageView old = view(conversation, 2, sender, null, NOW.minus(Duration.ofHours(49)));

        ConversationMember senderMember = ConversationMember.join(conversation, sender, MemberRole.MEMBER, NOW, 1);
        ConversationMember admin = ConversationMember.join(conversation, UUID.randomUUID(), MemberRole.ADMIN, NOW, 1);
        ConversationMember owner = ConversationMember.join(conversation, UUID.randomUUID(), MemberRole.OWNER, NOW, 1);
        ConversationMember other = ConversationMember.join(conversation, UUID.randomUUID(), MemberRole.MEMBER, NOW, 1);

        Instant expected = fresh.createdAt().plus(WINDOW);
        assertThat(MessageViews.deletableUntil(fresh, senderMember, WINDOW, NOW)).isEqualTo(expected);
        assertThat(MessageViews.deletableUntil(fresh, admin, WINDOW, NOW)).isEqualTo(expected);
        assertThat(MessageViews.deletableUntil(fresh, owner, WINDOW, NOW)).isEqualTo(expected);
        assertThat(MessageViews.deletableUntil(fresh, other, WINDOW, NOW)).isNull();
        assertThat(MessageViews.deletableUntil(old, senderMember, WINDOW, NOW)).isNull();
    }

    @Test
    void onizlemeYuzBirimdeKesilirVekilBolunmez() {
        assertThat(ReplyPreview.snippet(null)).isNull();
        assertThat(ReplyPreview.snippet("kısa")).isEqualTo("kısa");
        assertThat(ReplyPreview.snippet("x".repeat(150))).hasSize(100);
        assertThat(ReplyPreview.snippet("a".repeat(99) + "😀")).isEqualTo("a".repeat(99));
        assertThat(ReplyPreview.snippet("a".repeat(98) + "😀" + "b")).isEqualTo("a".repeat(98) + "😀");
    }

    private static MessageView view(UUID conversation, long seq, UUID sender, UUID replyTo, Instant createdAt) {
        return new MessageView(UUID.randomUUID(), conversation, seq, sender, MessageType.TEXT, "x", "c-" + seq,
                createdAt, null, null, null, replyTo, null, 0, false, ReactionsView.EMPTY, null);
    }
}
