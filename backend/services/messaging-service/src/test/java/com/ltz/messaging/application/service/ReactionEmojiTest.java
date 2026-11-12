package com.ltz.messaging.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;

/** MSG-23: tepki tek bir emojidir (ZWJ dizisi, ten rengi, bayrak, keycap dahil); başka her şey reddedilir. */
class ReactionEmojiTest {

    @ParameterizedTest
    @ValueSource(strings = {"❤️", "👍", "👍🏽", "👨‍👩‍👧‍👦", "🇹🇷", "1️⃣", "😂", "🔥"})
    void tekEmojiKabulEdilir(String emoji) {
        assertThat(ReactionEmoji.validate(emoji)).isEqualTo(emoji);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "ab", "1", "👍👍", " ", "❤️ ", " ❤️", "", "\uD83D", "<b>", "👍a", "\u0000",
            "🇹", "👨‍👩‍👧‍👦👨‍👩‍👧‍👦👨‍👩‍👧‍👦"})
    void emojiOlmayanYaDaBirdenFazlaGrafemReddedilir(String raw) {
        assertThatThrownBy(() -> ReactionEmoji.validate(raw))
                .isInstanceOfSatisfying(MessagingException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(MessagingErrorCode.INVALID_REACTION));
    }

    @Test
    void nullReddedilir() {
        assertThatThrownBy(() -> ReactionEmoji.validate(null)).isInstanceOf(MessagingException.class);
    }
}
