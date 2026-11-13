package com.artbid.chat.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatMessageTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 20, 0);

	@Test
	void 앞뒤_공백을_지우고_저장한다() {
		ChatMessage message = ChatMessage.of(1L, 7L, "홍길동", false, "  안녕하세요  ", NOW);

		assertThat(message.getContent()).isEqualTo("안녕하세요");
		assertThat(message.getCreatedAt()).isEqualTo(NOW);
		assertThat(message.isHost()).isFalse();
	}

	@Test
	void 공백뿐인_메시지는_만들_수_없다() {
		assertThatThrownBy(() -> ChatMessage.of(1L, 7L, "홍길동", false, "   ", NOW))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 최대_길이를_넘는_메시지는_만들_수_없다() {
		String tooLong = "가".repeat(ChatMessage.MAX_CONTENT_LENGTH + 1);

		assertThatThrownBy(() -> ChatMessage.of(1L, 7L, "홍길동", false, tooLong, NOW))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
